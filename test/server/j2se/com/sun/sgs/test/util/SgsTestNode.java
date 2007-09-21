/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.test.util;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.impl.auth.IdentityImpl;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.kernel.TaskOwnerImpl;
import com.sun.sgs.impl.service.channel.ChannelServiceImpl;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.net.DataStoreClient;
import com.sun.sgs.impl.service.nodemap.NodeMappingServerImpl;
import com.sun.sgs.impl.service.nodemap.NodeMappingServiceImpl;
import com.sun.sgs.impl.service.session.ClientSessionServiceImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.service.watchdog.WatchdogServiceImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.TaskOwner;
import com.sun.sgs.service.TransactionProxy;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * A node, used for testing.  The node is created using the kernel.
 * Multiple nodes can be created within a single VM.
 *
 */
public class SgsTestNode {
    // Reflective stuff.
    
    /** Kernel class */
    private static Class kernelClass;
    
    /** kernel constructor */
    private static Constructor kernelCtor;
    /** application startup method */
    private static Method kernelStartupMethod;
    /** kernel shutdown */
    private static Method kernelShutdownMethod;
    /** transaction proxy */
    private static Field kernelProxy;
    /** system registry */
    private static Field kernelReg;
    /** application context */
    private static Field kernelLastOwner;
    /** set current owner method */
    private static Method setCurrentOwnerMethod;
    /** get current owner method */
    private static Method getCurrentOwnerMethod;
    
    static {
        try {
            kernelClass =
                Class.forName("com.sun.sgs.impl.kernel.Kernel");
            kernelCtor =  
                kernelClass.getDeclaredConstructor(Properties.class);
            kernelCtor.setAccessible(true);

            kernelStartupMethod = 
                    kernelClass.getDeclaredMethod("startupApplication", 
                                                  Properties.class);
            kernelStartupMethod.setAccessible(true);

            kernelShutdownMethod = 
                    kernelClass.getDeclaredMethod("shutdown");
            kernelShutdownMethod.setAccessible(true);
            
            kernelProxy = kernelClass.getDeclaredField("transactionProxy");
            kernelProxy.setAccessible(true);

            kernelReg = kernelClass.getDeclaredField("systemRegistry");
            kernelReg.setAccessible(true);

            kernelLastOwner = kernelClass.getDeclaredField("lastOwner");
            kernelLastOwner.setAccessible(true);
            
            Class tsClass =
                Class.forName("com.sun.sgs.impl.kernel.ThreadState");
            setCurrentOwnerMethod =
                tsClass.getDeclaredMethod("setCurrentOwner", TaskOwner.class);
            setCurrentOwnerMethod.setAccessible(true);
            
            getCurrentOwnerMethod =
                tsClass.getDeclaredMethod("getCurrentOwner");
            getCurrentOwnerMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /** The app name. */
    private final String appName;

    /** The server node, or null. */
    private final SgsTestNode serverNode;
    
    /** The service properties. */
    public final Properties props;

    /** The name of the DB directory. */
    private final String dbDirectory;
    
    private final Object kernel;
    private final TransactionProxy txnProxy;
    private final ComponentRegistry systemRegistry;
    
    /** Services. */
    private final DataServiceImpl dataService;
    private final WatchdogServiceImpl watchdogService;
    private final NodeMappingServiceImpl nodeMappingService;
    private final TaskServiceImpl taskService;
    private final ClientSessionServiceImpl sessionService;
    private final ChannelServiceImpl channelService;
    
    /** The listen port for the client session service. */
    private int appPort;
    
    /**
     * Creates a new instance of SgsTestNode.
     *   
     * 
     * @param appName the application name
     * @param serverNode  the instance which created the servers,
     *                    {@code null} if this instance should create them
     * @param listenerClass the class of the listener object, or null if a
     *                     simple dummy listener should be used
     * @param properties serverProperties to be used, or {@code null} for 
     *                     defaults
     * @param clean if {@code true}, make sure the data store directory is 
     *                     fresh and set this thread's owner to one created
     *                     for this {@code SgsTestNode}
     */
    public SgsTestNode(String appName, 
                       SgsTestNode serverNode,
                       Class listenerClass,
                       Properties properties,
                       boolean clean)
        throws Exception
    {
        this.appName = appName;
	this.serverNode = serverNode;
        
	boolean isServerNode = serverNode == null;
	String startServer = String.valueOf(isServerNode);
	
	dbDirectory = System.getProperty("java.io.tmpdir") + File.separator + 
                       appName + ".db";
        if (clean) {
            deleteDirectory(dbDirectory);
            createDirectory(dbDirectory);
        }
        
        int requestedDataPort =
            isServerNode ?
            0 :
            getDataServerPort(serverNode.getProxy().getService(DataServiceImpl.class));
        
	int requestedWatchdogPort =
	    isServerNode ?
	    0 :
            serverNode.getProxy().
                getService(WatchdogServiceImpl.class).getServer().getPort();
        
	int requestedNodeMapPort =
	    isServerNode ?
	    0 :
	    getNodeMapServerPort(serverNode.getProxy().
                                getService(NodeMappingServiceImpl.class));
	
        // The node mapping service will only work if it's running
        // on a full stack.
        if (listenerClass == null) {
            listenerClass = DummyAppListener.class;
        }
	
        if (properties == null) {
            props = createProperties(
                "com.sun.sgs.app.name", appName,
                "com.sun.sgs.app.port", "0",
                "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
                    dbDirectory,
                "com.sun.sgs.impl.service.data.store.net.server.run", 
                    startServer,
                "com.sun.sgs.impl.service.data.store.net.server.port", 
                    String.valueOf(requestedDataPort),
                "com.sun.sgs.impl.service.data.DataServiceImpl.data.store.class",
                    "com.sun.sgs.impl.service.data.store.net.DataStoreClient",
                "com.sun.sgs.impl.service.data.store.net.server.host", 
                    "localhost",
                "com.sun.sgs.impl.service.watchdog.server.start", startServer,
                "com.sun.sgs.impl.service.watchdog.server.port",
                    String.valueOf(requestedWatchdogPort),
                "com.sun.sgs.impl.service.watchdog.renew.interval", "500",
                "com.sun.sgs.impl.service.nodemap.server.start", startServer,
                "com.sun.sgs.impl.service.nodemap.server.port",
                    String.valueOf(requestedNodeMapPort),
                "com.sun.sgs.impl.service.nodemap.remove.expire.time", "250",
                StandardProperties.APP_LISTENER, listenerClass.getName()
                );
        } else {
            props = properties;
        }
        kernel = kernelCtor.newInstance(props);
        kernelStartupMethod.invoke(kernel, props);
        
        // Wait for the application context to finish.  
        //
        // Note that if we could run the tests from within the system 
        // (i.e. from within a test service), we would not have to do this.
        // We'd simply wait for the test service's ready method to be called.
        // However, we also want to use the JUnit framework.  We'd probably 
        // want to use the JUnit version with annotations so it could find
        // the tests, and would need to coordinate JUnit's setup call and
        // our ready() call.
        TaskOwner owner = (TaskOwner) kernelLastOwner.get(kernel);
        while (owner == null) {
            Thread.currentThread().sleep(500);
            owner = (TaskOwner) kernelLastOwner.get(kernel);
        }
        
        TaskOwner oldOwner = (TaskOwner) getCurrentOwnerMethod.invoke(null);
        setCurrentOwnerMethod.invoke(null, owner);
        
        txnProxy = (TransactionProxy) kernelProxy.get(kernel);
        systemRegistry = (ComponentRegistry) kernelReg.get(kernel);
        
        dataService = txnProxy.getService(DataServiceImpl.class);
        watchdogService = txnProxy.getService(WatchdogServiceImpl.class);
        nodeMappingService = txnProxy.getService(NodeMappingServiceImpl.class);
        taskService = txnProxy.getService(TaskServiceImpl.class);
        sessionService = txnProxy.getService(ClientSessionServiceImpl.class);
        channelService = txnProxy.getService(ChannelServiceImpl.class);
                
        if (!clean) {
            // restore the old owner
            setCurrentOwnerMethod.invoke(null, oldOwner);
        }
        appPort = sessionService.getListenPort();
    }
    
    /**
     * Shut down this SgsTestNode.
     *
     * @param clean if {@code true}, also delete the data store directory
     */
    public void shutdown(boolean clean) throws Exception {
        kernelShutdownMethod.invoke(kernel);
        if (clean) {
            deleteDirectory(dbDirectory);
        }
    }
    
    /**
     * A simple application listener, used one is not specified when this
     * SgsTestNode is constructed.  Note that the node mapping service
     * notes only "full" stacks as being available for node assignment, so
     * we need to include an application listener.
     */
    public static class DummyAppListener implements AppListener, Serializable {

	private final static long serialVersionUID = 1L;

        /** {@inheritDoc} */
	public ClientSessionListener loggedIn(ClientSession session) {
            return null;
	}

        /** {@inheritDoc} */
	public void initialize(Properties props) {
	}
    }
    
    /**
     * Returns the transaction proxy.
     */
    public TransactionProxy getProxy() {
        return txnProxy;
    }

    /**
     * Returns the system registry.
     */
    public ComponentRegistry getSystemRegistry() {
        return systemRegistry;
    }
    
    /**
     * Returns the data service.
     */
    public DataServiceImpl getDataService() {
	return dataService;
    }

    /**
     * Returns the watchdog service.
     */
    public WatchdogServiceImpl getWatchdogService() {
	return watchdogService;
    }

    /**
     * Returns the node mapping service.
     */
    public NodeMappingServiceImpl getNodeMappingService() {
	return nodeMappingService;
    }
    
    /**
     * Returns the task service.
     */
    public TaskServiceImpl getTaskService() {
	return taskService;
    }
    /**
     * Returns the client session service.
     */
    public ClientSessionServiceImpl getClientSessionService() {
	return sessionService;
    }
    
    /**
     * Returns the channel service.
     */
    public ChannelServiceImpl getChannelService() {
	return channelService;
    }
    
    /**
     * Returns the service properties used for creating this node.
     */
    public Properties getServiceProperties() {
        return props;
    }
    
    /**
     * Returns the bound app port.
     */
    public int getAppPort() {
	return appPort;
    }
    
    /** Creates a property list with the specified keys and values. */
    private static Properties createProperties(String... args) {
        Properties props = new Properties();
        if (args.length % 2 != 0) {
            throw new RuntimeException("Odd number of arguments");
	        }
	        for (int i = 0; i < args.length; i += 2) {
	            props.setProperty(args[i], args[i + 1]);
	        }
	        return props;
	    }

    /** Creates the specified directory, if it does not already exist. */
    private static void createDirectory(String directory) {
        File dir = new File(directory);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                throw new RuntimeException(
                    "Problem creating directory: " + dir);
            }
        }
    }
   
    /** Deletes the specified directory, if it exists. */
    private static void deleteDirectory(String directory) {
        File dir = new File(directory);
        if (dir.exists()) {
            for (File f : dir.listFiles()) {
                if (!f.delete()) {
                    throw new RuntimeException("Failed to delete file: " + f);
                }
            }
            if (!dir.delete()) {
                throw new RuntimeException(
                    "Failed to delete directory: " + dir);
            }
        }
    }
    
    /**
     * Returns the bound port for the data server.
     */
    private static int getDataServerPort(DataServiceImpl service) 
        throws Exception
    {
        Field storeField = DataServiceImpl.class.getDeclaredField("store");
        storeField.setAccessible(true);
        DataStoreClient dsClient = (DataStoreClient) storeField.get(service);
        
        Field serverPortField = DataStoreClient.class.getDeclaredField("serverPort");
        serverPortField.setAccessible(true);
        return (Integer) serverPortField.get(dsClient);
        
    }
    
    /**
     * Returns the bound port for the node mapping server.
     */
    private static int getNodeMapServerPort(
	NodeMappingServiceImpl nodmapService)
	throws Exception
    {
        Field serverImplField = 
            NodeMappingServiceImpl.class.getDeclaredField("serverImpl");
        serverImplField.setAccessible(true);
        Method getPortMethod = 
                NodeMappingServerImpl.class.getDeclaredMethod("getPort");
        getPortMethod.setAccessible(true);
	NodeMappingServerImpl server =
	    (NodeMappingServerImpl) serverImplField.get(nodmapService);
	return (Integer) getPortMethod.invoke(server);
    }
}
