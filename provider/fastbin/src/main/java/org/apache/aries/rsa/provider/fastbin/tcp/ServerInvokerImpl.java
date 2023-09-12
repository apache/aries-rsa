/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.rsa.provider.fastbin.tcp;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.aries.rsa.provider.fastbin.api.Dispatched;
import org.apache.aries.rsa.provider.fastbin.api.ObjectSerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.api.Serialization;
import org.apache.aries.rsa.provider.fastbin.api.SerializationStrategy;
import org.apache.aries.rsa.provider.fastbin.io.ServerInvoker;
import org.apache.aries.rsa.provider.fastbin.io.Transport;
import org.apache.aries.rsa.provider.fastbin.io.TransportAcceptListener;
import org.apache.aries.rsa.provider.fastbin.io.TransportListener;
import org.apache.aries.rsa.provider.fastbin.io.TransportServer;
import org.apache.aries.rsa.provider.fastbin.streams.StreamProvider;
import org.apache.aries.rsa.provider.fastbin.streams.StreamProviderImpl;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.BufferEditor;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.fusesource.hawtdispatch.DispatchQueue;
import org.osgi.framework.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ServerInvokerImpl implements ServerInvoker, Dispatched {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ServerInvokerImpl.class);
    private static final HashMap<String, Class> PRIMITIVE_TO_CLASS = new HashMap<>(8, 1.0F);
    static {
        PRIMITIVE_TO_CLASS.put("Z", boolean.class);
        PRIMITIVE_TO_CLASS.put("B", byte.class);
        PRIMITIVE_TO_CLASS.put("C", char.class);
        PRIMITIVE_TO_CLASS.put("S", short.class);
        PRIMITIVE_TO_CLASS.put("I", int.class);
        PRIMITIVE_TO_CLASS.put("J", long.class);
        PRIMITIVE_TO_CLASS.put("F", float.class);
        PRIMITIVE_TO_CLASS.put("D", double.class);
    }

    protected final ExecutorService blockingExecutor = Executors.newFixedThreadPool(8);
    protected final DispatchQueue queue;
    private final Map<String, SerializationStrategy> serializationStrategies;
    protected final TransportServer server;
    protected final Map<UTF8Buffer, ServiceFactoryHolder> holders = new HashMap<>();
    private StreamProvider streamProvider;

    static class MethodData {

        private final SerializationStrategy serializationStrategy;
        final InvocationStrategy invocationStrategy;
        final Method method;

        MethodData(InvocationStrategy invocationStrategy, SerializationStrategy serializationStrategy, Method method) {
            this.invocationStrategy = invocationStrategy;
            this.serializationStrategy = serializationStrategy;
            this.method = method;
        }
    }

    class ServiceFactoryHolder {

        private final ServiceFactory factory;
        private final ClassLoader loader;
        private final Class clazz;
        private HashMap<Buffer, MethodData> method_cache = new HashMap<>();

        public ServiceFactoryHolder(ServiceFactory factory, ClassLoader loader) {
            this.factory = factory;
            this.loader = loader;
            Object o = factory.get();
            clazz = o.getClass();
            factory.unget();
        }

        private MethodData getMethodData(Buffer data) throws IOException, NoSuchMethodException, ClassNotFoundException {
            MethodData rc = method_cache.get(data);
            if( rc == null ) {
                String[] parts = data.utf8().toString().split(",");
                String name = parts[0];
                Class[] params = new Class[parts.length - 1];
                for( int i = 0; i < params.length; i++) {
                    params[i] = decodeClass(parts[i + 1]);
                }
                Method method = clazz.getMethod(name, params);

                Serialization annotation = method.getAnnotation(Serialization.class);
                SerializationStrategy serializationStrategy;
                if( annotation!=null ) {
                    serializationStrategy = serializationStrategies.get(annotation.value());
                    if( serializationStrategy==null ) {
                        throw new RuntimeException("Could not find the serialization strategy named: "+annotation.value());
                    }
                } else {
                    serializationStrategy = ObjectSerializationStrategy.INSTANCE;
                }

                final InvocationStrategy invocationStrategy = InvocationType.forMethod(method);

                rc = new MethodData(invocationStrategy, serializationStrategy, method);
                method_cache.put(data, rc);
            }
            return rc;
        }

        private Class<?> decodeClass(String s) throws ClassNotFoundException {
            if( s.startsWith("[")) {
                Class<?> nested = decodeClass(s.substring(1));
                return Array.newInstance(nested, 0).getClass();
            }
            String c = s.substring(0, 1);
            if( c.equals("L") ) {
                return loader.loadClass(s.substring(1));
            } else {
                return PRIMITIVE_TO_CLASS.get(c);
            }
        }

    }

    public ServerInvokerImpl(String address, DispatchQueue queue, Map<String, SerializationStrategy> serializationStrategies) throws Exception {
        this.queue = queue;
        this.serializationStrategies = serializationStrategies;
        this.server = new TcpTransportFactory().bind(address);
        this.server.setDispatchQueue(queue);
        this.server.setAcceptListener(new InvokerAcceptListener());
    }

    public InetSocketAddress getSocketAddress() {
        return this.server.getSocketAddress();
    }

    public DispatchQueue queue() {
        return queue;
    }

    public String getConnectAddress() {
        return this.server.getConnectAddress();
    }

    @Override
    public StreamProvider getStreamProvider() {
        return streamProvider;
    }

    public void registerService(final String id, final ServiceFactory service, final ClassLoader classLoader) {
        queue().execute(new Runnable() {
            public void run() {
                holders.put(new UTF8Buffer(id), new ServiceFactoryHolder(service, classLoader));
            }
        });
    }

    public void unregisterService(final String id) {
        queue().execute(new Runnable() {
            public void run() {
                holders.remove(new UTF8Buffer(id));
            }
        });
    }

    public void start() throws Exception {
        start(null);
    }

    public void start(Runnable onComplete) throws Exception {
        registerStreamProvider();
        this.server.start(onComplete);
    }

    private void registerStreamProvider() {
        streamProvider = new StreamProviderImpl();
        registerService(StreamProvider.STREAM_PROVIDER_SERVICE_NAME, new ServerInvoker.ServiceFactory() {

            @Override
            public Object get() {
                return streamProvider;
            }

            @Override
            public void unget(){
                // nothing to do
            }
        }, getClass().getClassLoader());

    }

    public void stop() {
        stop(null);
    }

    public void stop(final Runnable onComplete) {
        this.server.stop(new Runnable() {
            public void run() {
                blockingExecutor.shutdown();
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    protected void onCommand(final Transport transport, Object data) {
        try {
            final DataByteArrayInputStream bais = new DataByteArrayInputStream((Buffer) data);
            final int size = bais.readInt();
            final long correlation = bais.readVarLong();

            // Use UTF8Buffer instead of string to avoid encoding/decoding UTF-8 strings
            // for every request.
            final UTF8Buffer service = readBuffer(bais).utf8();
            final Buffer encoded_method = readBuffer(bais);

            final ServiceFactoryHolder holder = holders.get(service);
            Runnable task = null;
            if(holder==null) {
                String message = "The requested service {"+service+"} is not available";
                LOGGER.warn(message);
                task = new SendTask(bais, correlation, transport, message);
            }
            final Object svc = holder==null ? null : holder.factory.get();
            if(holder!=null) {
                try {
                    final MethodData methodData = holder.getMethodData(encoded_method);
                    task = new SendTask(svc, bais, holder, correlation, methodData, transport);
                }
                catch (ReflectiveOperationException reflectionEx) {
                    final String methodName = encoded_method.utf8().toString();
                    String message = "The requested method {"+methodName+"} is not available";
                    LOGGER.warn(message);
                    task = new SendTask(bais, correlation, transport, message);
                }
            }

            Executor executor;
            if( svc instanceof Dispatched ) {
                executor = ((Dispatched)svc).queue();
            } else {
                executor = blockingExecutor;
            }
            executor.execute(task);

        } catch (Exception e) {
            LOGGER.info("Error while reading request", e);
        }
    }

    private Buffer readBuffer(DataByteArrayInputStream bais) throws IOException {
        byte[] b = new byte[bais.readVarInt()];
        bais.readFully(b);
        return new Buffer(b);
    }

    class InvokerAcceptListener implements TransportAcceptListener {

        public void onAccept(TransportServer transportServer, TcpTransport transport) {
            transport.setProtocolCodec(new LengthPrefixedCodec());
            transport.setDispatchQueue(queue());
            transport.setTransportListener(new InvokerTransportListener());
            transport.start();
        }

        public void onAcceptError(TransportServer transportServer, Exception error) {
            LOGGER.info("Error accepting incoming connection", error);
        }
    }

    class InvokerTransportListener implements TransportListener {

        public void onTransportCommand(Transport transport, Object command) {
            ServerInvokerImpl.this.onCommand(transport, command);
        }

        public void onRefill(Transport transport) {
        }

        public void onTransportFailure(Transport transport, IOException error) {
            if (!transport.isDisposed() && !(error instanceof EOFException)) {
                LOGGER.info("Transport failure", error);
            }
        }

        public void onTransportConnected(Transport transport) {
            transport.resumeRead();
        }

        public void onTransportDisconnected(Transport transport) {
        }
    }

    private final class SendTask implements Runnable {
        private Object svc;
        private DataByteArrayInputStream bais;
        private ServiceFactoryHolder holder;
        private long correlation;
        private MethodData methodData;
        private Transport transport;

        private SendTask(Object svc, DataByteArrayInputStream bais, ServiceFactoryHolder holder, long correlation, MethodData methodData, Transport transport) {
            this.svc = svc;
            this.bais = bais;
            this.holder = holder;
            this.correlation = correlation;
            this.methodData = methodData;
            this.transport = transport;
        }

        private SendTask(DataByteArrayInputStream bais, long correlation, Transport transport, String errorMessage) {
            this(new ServiceException(errorMessage), bais, null, correlation, new MethodData(new BlockingInvocationStrategy(), ObjectSerializationStrategy.INSTANCE, null), transport);
        }

        public void run() {

            final DataByteArrayOutputStream baos = new DataByteArrayOutputStream();
            try {
                baos.writeInt(0); // make space for the size field.
                baos.writeVarLong(correlation);
            } catch (IOException e) { // should not happen
                LOGGER.error("Failed to write to buffer", e);
                throw new RuntimeException(e);
            }

            // Lets decode the remaining args on the target's executor
            // to take cpu load off the

            ClassLoader loader = holder==null ? getClass().getClassLoader() : holder.loader;
            methodData.invocationStrategy.service(methodData.serializationStrategy, loader, methodData.method, svc, bais, baos, new Runnable() {
                public void run() {
                    if(holder!=null)
                        holder.factory.unget();
                    final Buffer command = baos.toBuffer();

                    // Update the size field.
                    BufferEditor editor = command.buffer().bigEndianEditor();
                    editor.writeInt(command.length);

                    queue().execute(new Runnable() {
                        public void run() {
                            transport.offer(command);
                        }
                    });
                }
            });
        }
    }

}
