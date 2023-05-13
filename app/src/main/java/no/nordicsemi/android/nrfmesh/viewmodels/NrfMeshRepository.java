package no.nordicsemi.android.nrfmesh.viewmodels;

import static no.nordicsemi.android.mesh.opcodes.ApplicationMessageOpCodes.GENERIC_LEVEL_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ApplicationMessageOpCodes.GENERIC_ON_OFF_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ApplicationMessageOpCodes.SCENE_REGISTER_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ApplicationMessageOpCodes.SCENE_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_APPKEY_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_DEFAULT_TTL_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_GATT_PROXY_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_HEARTBEAT_PUBLICATION_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_HEARTBEAT_SUBSCRIPTION_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_MODEL_APP_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_MODEL_PUBLICATION_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_NODE_RESET_STATUS;
import static no.nordicsemi.android.mesh.opcodes.ConfigMessageOpCodes.CONFIG_RELAY_STATUS;
import static no.nordicsemi.android.nrfmesh.ble.BleMeshManager.MESH_PROXY_UUID;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import no.nordicsemi.android.ble.observer.ConnectionObserver;
import no.nordicsemi.android.mesh.ApplicationKey;
import no.nordicsemi.android.mesh.Group;
import no.nordicsemi.android.mesh.MeshManagerApi;
import no.nordicsemi.android.mesh.MeshManagerCallbacks;
import no.nordicsemi.android.mesh.MeshNetwork;
import no.nordicsemi.android.mesh.MeshProvisioningStatusCallbacks;
import no.nordicsemi.android.mesh.MeshStatusCallbacks;
import no.nordicsemi.android.mesh.NetworkKey;
import no.nordicsemi.android.mesh.Provisioner;
import no.nordicsemi.android.mesh.UnprovisionedBeacon;
import no.nordicsemi.android.mesh.models.SigModelParser;
import no.nordicsemi.android.mesh.opcodes.ProxyConfigMessageOpCodes;
import no.nordicsemi.android.mesh.provisionerstates.ProvisioningState;
import no.nordicsemi.android.mesh.provisionerstates.UnprovisionedMeshNode;
import no.nordicsemi.android.mesh.transport.ConfigAppKeyAdd;
import no.nordicsemi.android.mesh.transport.ConfigAppKeyStatus;
import no.nordicsemi.android.mesh.transport.ConfigCompositionDataGet;
import no.nordicsemi.android.mesh.transport.ConfigCompositionDataStatus;
import no.nordicsemi.android.mesh.transport.ConfigDefaultTtlGet;
import no.nordicsemi.android.mesh.transport.ConfigDefaultTtlStatus;
import no.nordicsemi.android.mesh.transport.ConfigGattProxyStatus;
import no.nordicsemi.android.mesh.transport.ConfigModelAppStatus;
import no.nordicsemi.android.mesh.transport.ConfigModelPublicationStatus;
import no.nordicsemi.android.mesh.transport.ConfigModelSubscriptionStatus;
import no.nordicsemi.android.mesh.transport.ConfigNetworkTransmitSet;
import no.nordicsemi.android.mesh.transport.ConfigNetworkTransmitStatus;
import no.nordicsemi.android.mesh.transport.ConfigNodeResetStatus;
import no.nordicsemi.android.mesh.transport.ConfigRelayStatus;
import no.nordicsemi.android.mesh.transport.ControlMessage;
import no.nordicsemi.android.mesh.transport.Element;
import no.nordicsemi.android.mesh.transport.GenericLevelStatus;
import no.nordicsemi.android.mesh.transport.GenericOnOffStatus;
import no.nordicsemi.android.mesh.transport.MeshMessage;
import no.nordicsemi.android.mesh.transport.MeshModel;
import no.nordicsemi.android.mesh.transport.ProvisionedMeshNode;
import no.nordicsemi.android.mesh.transport.ProxyConfigFilterStatus;
import no.nordicsemi.android.mesh.transport.SceneRegisterStatus;
import no.nordicsemi.android.mesh.transport.SceneStatus;
import no.nordicsemi.android.mesh.transport.VendorModelMessageStatus;
import no.nordicsemi.android.mesh.utils.MeshAddress;
import no.nordicsemi.android.nrfmesh.R;
import no.nordicsemi.android.nrfmesh.adapter.ExtendedBluetoothDevice;
import no.nordicsemi.android.nrfmesh.ble.BleMeshManager;
import no.nordicsemi.android.nrfmesh.ble.MeshDataObserver;
import no.nordicsemi.android.nrfmesh.utils.ProvisionerStates;
import no.nordicsemi.android.nrfmesh.utils.Utils;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanRecord;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

@Singleton
public class NrfMeshRepository implements MeshProvisioningStatusCallbacks, MeshStatusCallbacks, MeshManagerCallbacks, MeshDataObserver, ConnectionObserver {

    private static final String TAG = NrfMeshRepository.class.getSimpleName();
    private static final int ATTENTION_TIMER = 5;
    static final String EXPORT_PATH = Environment.getExternalStorageDirectory() + File.separator +
            "Nordic Semiconductor" + File.separator + "nRF Mesh" + File.separator;

    // Connection States Connecting, Connected, Disconnecting, Disconnected etc.
    private final MutableLiveData<Boolean> mIsConnectedToProxy = new MutableLiveData<>();

    // Live data flag containing connected state.
    private MutableLiveData<Boolean> mIsConnected;

    // LiveData to notify when device is ready
    private final MutableLiveData<Void> mOnDeviceReady = new MutableLiveData<>();

    // Updates the connection state while connecting to a peripheral
    private final MutableLiveData<String> mConnectionState = new MutableLiveData<>();

    // Flag to determine if a reconnection is in the progress when provisioning has completed
    private final SingleLiveEvent<Boolean> mIsReconnecting = new SingleLiveEvent<>();
    private final MutableLiveData<UnprovisionedMeshNode> mUnprovisionedMeshNodeLiveData = new MutableLiveData<>();
    private final MutableLiveData<ProvisionedMeshNode> mProvisionedMeshNodeLiveData = new MutableLiveData<>();
    private final SingleLiveEvent<Integer> mConnectedProxyAddress = new SingleLiveEvent<>();

    private boolean mIsProvisioningComplete = false; // Flag to determine if provisioning was completed

    // Holds the selected MeshNode to configure
    private final MutableLiveData<ProvisionedMeshNode> mExtendedMeshNode = new MutableLiveData<>();

    // Holds the selected Element to configure
    private final MutableLiveData<Element> mSelectedElement = new MutableLiveData<>();

    // Holds the selected mesh model to configure
    private final MutableLiveData<MeshModel> mSelectedModel = new MutableLiveData<>();

    // Holds the selected provisioner when adding/editing
    private final MutableLiveData<Provisioner> mSelectedProvisioner = new MutableLiveData<>();

    private final MutableLiveData<Group> mSelectedGroupLiveData = new MutableLiveData<>();


    //Contains the MeshNetwork
    private final MeshNetworkLiveData mMeshNetworkLiveData = new MeshNetworkLiveData();
    private final SingleLiveEvent<String> mNetworkImportState = new SingleLiveEvent<>();
    private final SingleLiveEvent<MeshMessage> mMeshMessageLiveData = new SingleLiveEvent<>();

    // Contains the provisioned nodes
    private final MutableLiveData<List<ProvisionedMeshNode>> mProvisionedNodes = new MutableLiveData<>();

    private final MutableLiveData<List<Group>> mGroups = new MutableLiveData<>();

    private final MutableLiveData<TransactionStatus> mTransactionStatus = new SingleLiveEvent<>();

    private final MeshManagerApi mMeshManagerApi;
    private final BleMeshManager mBleMeshManager;
    private final Handler mHandler;
    private UnprovisionedMeshNode mUnprovisionedMeshNode;
    private ProvisionedMeshNode mProvisionedMeshNode;
    private boolean mIsReconnectingFlag;
    private boolean mIsScanning;
    private boolean mSetupProvisionedNode;
    private boolean mIsFFanInitializing = false;
    private ProvisioningStatusLiveData mProvisioningStateLiveData;
    private MeshNetwork mMeshNetwork;
    private boolean mIsCompositionDataReceived;
    private boolean mIsDefaultTtlReceived;
    private boolean mIsAppKeyAddCompleted;
    private boolean mIsNetworkRetransmitSetCompleted;


    private final Runnable mReconnectRunnable = this::startScan;

    private final Runnable mScannerTimeout = () -> {
        Log.d(TAG, "Timeout, Scan stopped!");
        stopScan();
        mIsReconnecting.postValue(false);
    };

    @Inject
    public NrfMeshRepository(final MeshManagerApi meshManagerApi,
                             final BleMeshManager bleMeshManager) {
        //Initialize the mesh api
        mMeshManagerApi = meshManagerApi;
        mMeshManagerApi.setMeshManagerCallbacks(this);
        mMeshManagerApi.setProvisioningStatusCallbacks(this);
        mMeshManagerApi.setMeshStatusCallbacks(this);
        mMeshManagerApi.loadMeshNetwork();
        //Initialize the ble manager
        mBleMeshManager = bleMeshManager;
        mBleMeshManager.setDataObserver(this);
        mBleMeshManager.setConnectionObserver(this);
        mHandler = new Handler(Looper.getMainLooper());
    }


    /**
     * Returns {@link SingleLiveEvent} containing the device ready state.
     */
    LiveData<Void> isDeviceReady() {
        return mOnDeviceReady;
    }

    /**
     * Returns {@link SingleLiveEvent} containing the device ready state.
     */
    LiveData<String> getConnectionState() {
        return mConnectionState;
    }

    /**
     * Returns {@link SingleLiveEvent} containing the device ready state.
     */
    LiveData<Boolean> isConnected() {
        return mIsConnected;
    }

    /**
     * Returns {@link SingleLiveEvent} containing the device ready state.
     */
    LiveData<Boolean> isConnectedToProxy() {
        return mIsConnectedToProxy;
    }

    LiveData<Boolean> isReconnecting() {
        return mIsReconnecting;
    }

    boolean isProvisioningComplete() {
        return mIsProvisioningComplete;
    }

    boolean isCompositionDataStatusReceived() {
        return mIsCompositionDataReceived;
    }

    boolean isDefaultTtlReceived() {
        return mIsDefaultTtlReceived;
    }

    boolean isAppKeyAddCompleted() {
        return mIsAppKeyAddCompleted;
    }

    boolean isNetworkRetransmitSetCompleted() {
        return mIsNetworkRetransmitSetCompleted;
    }

    final MeshNetworkLiveData getMeshNetworkLiveData() {
        return mMeshNetworkLiveData;
    }

    LiveData<List<ProvisionedMeshNode>> getNodes() {
        return mProvisionedNodes;
    }


    LiveData<String> getNetworkLoadState() {
        return mNetworkImportState;
    }

    ProvisioningStatusLiveData getProvisioningState() {
        return mProvisioningStateLiveData;
    }

    LiveData<TransactionStatus> getTransactionStatus() {
        return mTransactionStatus;
    }

    /**
     * Clears the transaction status
     */
    void clearTransactionStatus() {
        if (mTransactionStatus.getValue() != null) {
            mTransactionStatus.postValue(null);
        }
    }

    /**
     * Returns the mesh manager api
     *
     * @return {@link MeshManagerApi}
     */
    MeshManagerApi getMeshManagerApi() {
        return mMeshManagerApi;
    }

    /**
     * Returns the ble mesh manager
     *
     * @return {@link BleMeshManager}
     */
    BleMeshManager getBleMeshManager() {
        return mBleMeshManager;
    }

    /**
     * Returns the {@link MeshMessageLiveData} live data object containing the mesh message
     */
    LiveData<MeshMessage> getMeshMessageLiveData() {
        return mMeshMessageLiveData;
    }

    LiveData<Group> getSelectedGroup() {
        return mSelectedGroupLiveData;
    }

    /**
     * Reset mesh network
     */
    void resetMeshNetwork() {
        Log.d(TAG, "resetMeshNetwork");
        disconnect();
        mMeshManagerApi.resetMeshNetwork();
    }

    /**
     * Connect to peripheral
     *
     * @param context          Context
     * @param device           {@link ExtendedBluetoothDevice} device
     * @param connectToNetwork True if connecting to an unprovisioned node or proxy node
     */
    void connect(final Context context, final ExtendedBluetoothDevice device, final boolean connectToNetwork) {
        mMeshNetworkLiveData.setNodeName(device.getName());
        mIsProvisioningComplete = false;
        mIsCompositionDataReceived = false;
        mIsDefaultTtlReceived = false;
        mIsAppKeyAddCompleted = false;
        mIsNetworkRetransmitSetCompleted = false;
        //clearExtendedMeshNode();
        initIsConnectedLiveData(connectToNetwork);
        // mConnectionState.postValue("Connecting....");
        mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_connecting));
        //Added a 1 second delay for connection, mostly to wait for a disconnection to complete before connecting, in case a device was previously connected.
        Log.d(TAG, "Connect issued");
        mBleMeshManager.connect(device.getDevice()).retry(3, 200).enqueue();
    }

    /**
     * Connect to peripheral
     *
     * @param device bluetooth device
     */
    private void connectToProxy(final ExtendedBluetoothDevice device) {
        Log.d(TAG, "connect To Proxy " + device.getAddress());
        initIsConnectedLiveData(true);
        // mConnectionState.postValue("Connecting....");
        mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_connecting));
        mBleMeshManager.connect(device.getDevice()).retry(3, 200).enqueue();
    }

    private void initIsConnectedLiveData(final boolean connectToNetwork) {
        if (connectToNetwork) {
            mIsConnected = new SingleLiveEvent<>();
        } else {
            mIsConnected = new MutableLiveData<>();
        }
    }

    /**
     * Disconnects from peripheral
     */
    void disconnect() {
        clearProvisioningLiveData();
        mIsProvisioningComplete = false;
        Log.d(TAG, "Disconnect issued");
        mBleMeshManager.disconnect().enqueue();
    }

    void clearProvisioningLiveData() {
        Log.d(TAG, "Clear provisioning LiveData, Scan stopped!");
        stopScan();
        mHandler.removeCallbacks(mReconnectRunnable);
        mSetupProvisionedNode = false;
        mIsFFanInitializing = false;
        mIsReconnectingFlag = false;
        mUnprovisionedMeshNodeLiveData.setValue(null);
        mProvisionedMeshNodeLiveData.setValue(null);
    }

    public void identifyNode(final ExtendedBluetoothDevice device) {
        final UnprovisionedBeacon beacon = (UnprovisionedBeacon) device.getBeacon();
        if (beacon != null) {
            mMeshManagerApi.identifyNode(beacon.getUuid(), ATTENTION_TIMER);
        } else {
            final byte[] serviceData = Utils.getServiceData(device.getScanResult(), BleMeshManager.MESH_PROVISIONING_UUID);
            if (serviceData != null) {
                final UUID uuid = mMeshManagerApi.getDeviceUuid(serviceData);
                mMeshManagerApi.identifyNode(uuid, ATTENTION_TIMER);
            }
        }
    }

    private void clearExtendedMeshNode() {
        mExtendedMeshNode.postValue(null);
    }

    LiveData<UnprovisionedMeshNode> getUnprovisionedMeshNode() {
        return mUnprovisionedMeshNodeLiveData;
    }

    LiveData<Integer> getConnectedProxyAddress() {
        return mConnectedProxyAddress;
    }

    /**
     * Returns the selected mesh node
     */
    LiveData<ProvisionedMeshNode> getSelectedMeshNode() {
        return mExtendedMeshNode;
    }

    /**
     * Sets the mesh node to be configured
     *
     * @param node provisioned mesh node
     */
    void setSelectedMeshNode(final ProvisionedMeshNode node) {
        mProvisionedMeshNode = node;
        mExtendedMeshNode.postValue(node);
    }

    /**
     * Returns the selected element
     */
    LiveData<Element> getSelectedElement() {
        return mSelectedElement;
    }

    /**
     * Set the selected {@link Element} to be configured
     *
     * @param element element
     */
    void setSelectedElement(final Element element) {
        mSelectedElement.postValue(element);
    }


    /**
     * Selects provisioner for editing or adding
     *
     * @param provisioner {@link Provisioner}
     */
    void setSelectedProvisioner(@NonNull final Provisioner provisioner) {
        mSelectedProvisioner.postValue(provisioner);
    }

    /**
     * Returns the selected {@link Provisioner}
     */
    LiveData<Provisioner> getSelectedProvisioner() {
        return mSelectedProvisioner;
    }

    /**
     * Returns the selected mesh model
     */
    LiveData<MeshModel> getSelectedModel() {
        return mSelectedModel;
    }

    /**
     * Set the selected model to be configured
     *
     * @param model mesh model
     */
    void setSelectedModel(final MeshModel model) {
        mSelectedModel.postValue(model);
    }

    @Override
    public void onDataReceived(final BluetoothDevice bluetoothDevice, final int mtu, final byte[] pdu) {
        mMeshManagerApi.handleNotifications(mtu, pdu);
    }

    @Override
    public void onDataSent(final BluetoothDevice device, final int mtu, final byte[] pdu) {
        mMeshManagerApi.handleWriteCallbacks(mtu, pdu);
    }

    @Override
    public void onDeviceConnecting(@NonNull final BluetoothDevice device) {
        Log.d(TAG, device.getAddress() + " connecting....");
        // mConnectionState.postValue("Connecting....");
        mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_connecting));
    }

    @Override
    public void onDeviceConnected(@NonNull final BluetoothDevice device) {
        Log.d(TAG, device.getAddress() + " connected!");
        mIsConnected.postValue(true);
        // mConnectionState.postValue("Discovering services....");
        mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_discovering));
        mIsConnectedToProxy.postValue(true);
    }

    @Override
    public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
        Log.d(TAG, "Failed to connect to " + device.getAddress() + ", Reason: " + reason);
    }

    @Override
    public void onDeviceDisconnecting(@NonNull final BluetoothDevice device) {
        Log.d(TAG, device.getAddress() + " disconnecting...");
        if (mIsReconnectingFlag) {
            // mConnectionState.postValue("Reconnecting...");
            mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_reconnecting));
        } else {
            // mConnectionState.postValue("Disconnecting...");
            mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_disconnecting));
        }
    }

    @Override
    public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
        Log.d(TAG, device.getAddress() + " disconnected: " + reason);
        // mConnectionState.postValue("");
        mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_disconnected));
        if (mIsReconnectingFlag) {
            mIsReconnectingFlag = false;
            mIsReconnecting.postValue(false);
            mIsConnected.postValue(false);
            mIsConnectedToProxy.postValue(false);
        } else {
            mIsConnected.postValue(false);
            mIsConnectedToProxy.postValue(false);
            if (mConnectedProxyAddress.getValue() != null) {
                final MeshNetwork network = mMeshManagerApi.getMeshNetwork();
                if (network != null) {
                    network.setProxyFilter(null);
                }
            }
            //clearExtendedMeshNode();
        }
        mSetupProvisionedNode = false;
        mIsFFanInitializing = false;
        mConnectedProxyAddress.postValue(null);
    }

    @Override
    public void onDeviceReady(@NonNull final BluetoothDevice device) {
        Log.d(TAG, device.getAddress() + " Ready");
        mOnDeviceReady.postValue(null);

        if (mBleMeshManager.isProvisioningComplete()) {
            if (mSetupProvisionedNode) {
                if (mMeshNetwork.getSelectedProvisioner().getProvisionerAddress() != null) {
                    mHandler.postDelayed(() -> {
                        //Adding a slight delay here so we don't send anything before we receive the mesh beacon message
                        final ProvisionedMeshNode node = mProvisionedMeshNodeLiveData.getValue();
                        if (node != null) {
                            final ConfigCompositionDataGet compositionDataGet = new ConfigCompositionDataGet();
                            mMeshManagerApi.createMeshPdu(node.getUnicastAddress(), compositionDataGet);
                        }
                    }, 2000);
                } else {
                    mSetupProvisionedNode = false;
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.PROVISIONER_UNASSIGNED);
                    clearExtendedMeshNode();
                }
            }
            mIsConnectedToProxy.postValue(true);
        }
    }


    @Override
    public void onNetworkLoaded(final MeshNetwork meshNetwork) {
        loadNetwork(meshNetwork);
    }

    @Override
    public void onNetworkUpdated(final MeshNetwork meshNetwork) {
        loadNetwork(meshNetwork);
        updateSelectedGroup();
    }

    @Override
    public void onNetworkLoadFailed(final String error) {
        mNetworkImportState.postValue(error);
    }

    @Override
    public void onNetworkImported(final MeshNetwork meshNetwork) {
        loadNetwork(meshNetwork);
        mNetworkImportState.postValue(meshNetwork.getMeshName() + " has been successfully imported.\n" +
                "In order to start sending messages to this network, please change the provisioner address. " +
                "Using the same provisioner address will cause messages to be discarded due to the usage of incorrect sequence numbers " +
                "for this address. However if the network does not contain any nodes you do not need to change the address");
    }

    @Override
    public void onNetworkImportFailed(final String error) {
        mNetworkImportState.postValue(error);
    }

    @Override
    public void sendProvisioningPdu(final UnprovisionedMeshNode meshNode, final byte[] pdu) {
        mBleMeshManager.sendPdu(pdu);
    }

    @Override
    public void onMeshPduCreated(final byte[] pdu) {
        mBleMeshManager.sendPdu(pdu);
    }

    @Override
    public int getMtu() {
        return mBleMeshManager.getMaximumPacketSize();
    }

    @Override
    public void onProvisioningStateChanged(final UnprovisionedMeshNode meshNode, final ProvisioningState.States state, final byte[] data) {
        mUnprovisionedMeshNode = meshNode;
        mUnprovisionedMeshNodeLiveData.postValue(meshNode);
        switch (state) {
            case PROVISIONING_INVITE:
                mProvisioningStateLiveData = new ProvisioningStatusLiveData();
                break;
            case PROVISIONING_FAILED:
                mIsProvisioningComplete = false;
                break;
        }
        mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.fromStatusCode(state.getState()));
    }

    @Override
    public void onProvisioningFailed(final UnprovisionedMeshNode meshNode, final ProvisioningState.States state, final byte[] data) {
        Log.d(TAG, meshNode.getDeviceUuid() + " Provisioning Failed: " + state.name());
        mUnprovisionedMeshNode = meshNode;
        mUnprovisionedMeshNodeLiveData.postValue(meshNode);
        if (state == ProvisioningState.States.PROVISIONING_FAILED) {
            mIsProvisioningComplete = false;
        }
        mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.fromStatusCode(state.getState()));
    }

    @Override
    public void onProvisioningCompleted(final ProvisionedMeshNode meshNode, final ProvisioningState.States state, final byte[] data) {
        Log.d(TAG, meshNode.getUuid() + " Provisioning Completed: " + state.name());
        mProvisionedMeshNode = meshNode;
        mExtendedMeshNode.postValue(meshNode);
        mUnprovisionedMeshNodeLiveData.postValue(null);
        mProvisionedMeshNodeLiveData.postValue(meshNode);
        if (state == ProvisioningState.States.PROVISIONING_COMPLETE) {
            onProvisioningCompleted(meshNode);
        }
        mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.fromStatusCode(state.getState()));
    }

    private void onProvisioningCompleted(final ProvisionedMeshNode node) {
        mIsProvisioningComplete = true;
        mProvisionedMeshNode = node;
        mIsReconnecting.postValue(true);
        Log.d(TAG, "Disconnect issued on Provisioning Completed");
        mBleMeshManager.disconnect().enqueue();
        loadNodes();
        // mHandler.post(() -> mConnectionState.postValue("Scanning for provisioned node"));

        mHandler.post(() -> mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_scanning)));
        mHandler.postDelayed(mReconnectRunnable, 1000); //Added a slight delay to disconnect and refresh the cache
    }

    /**
     * Here we load all nodes except the current provisioner. This may contain other provisioner nodes if available
     */
    private void loadNodes() {
        final List<ProvisionedMeshNode> nodes = new ArrayList<>();
        for (final ProvisionedMeshNode node : mMeshNetwork.getNodes()) {
            if (!node.getUuid().equalsIgnoreCase(mMeshNetwork.getSelectedProvisioner().getProvisionerUuid())) {
                nodes.add(node);
            }
        }
        mProvisionedNodes.postValue(nodes);
    }

    @Override
    public void onTransactionFailed(final int dst, final boolean hasIncompleteTimerExpired) {
        mProvisionedMeshNode = mMeshNetwork.getNode(dst);
        mTransactionStatus.postValue(new TransactionStatus(dst, hasIncompleteTimerExpired));
    }

    @Override
    public void onUnknownPduReceived(final int src, final byte[] accessPayload) {
        final ProvisionedMeshNode node = mMeshNetwork.getNode(src);
        if (node != null) {
            updateNode(node);
        }
    }

    @Override
    public void onBlockAcknowledgementProcessed(final int dst, @NonNull final ControlMessage message) {
        final ProvisionedMeshNode node = mMeshNetwork.getNode(dst);
        if (node != null) {
            mProvisionedMeshNode = node;
            if (mSetupProvisionedNode) {
                mProvisionedMeshNodeLiveData.postValue(mProvisionedMeshNode);
                mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.SENDING_BLOCK_ACKNOWLEDGEMENT);
            }
        }
    }

    @Override
    public void onBlockAcknowledgementReceived(final int src, @NonNull final ControlMessage message) {
        final ProvisionedMeshNode node = mMeshNetwork.getNode(src);
        if (node != null) {
            mProvisionedMeshNode = node;
            if (mSetupProvisionedNode) {
                mProvisionedMeshNodeLiveData.postValue(node);
                mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.BLOCK_ACKNOWLEDGEMENT_RECEIVED);
            }
        }
    }

    @Override
    public void onMeshMessageProcessed(final int dst, @NonNull final MeshMessage meshMessage) {
        final ProvisionedMeshNode node = mMeshNetwork.getNode(dst);
        if (node != null) {
            mProvisionedMeshNode = node;
            if (meshMessage instanceof ConfigCompositionDataGet) {
                if (mSetupProvisionedNode) {
                    mProvisionedMeshNodeLiveData.postValue(node);
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.COMPOSITION_DATA_GET_SENT);
                }
            } else if (meshMessage instanceof ConfigDefaultTtlGet) {
                if (mSetupProvisionedNode) {
                    mProvisionedMeshNodeLiveData.postValue(node);
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.SENDING_DEFAULT_TTL_GET);
                }
            } else if (meshMessage instanceof ConfigAppKeyAdd) {
                if (mSetupProvisionedNode) {
                    mProvisionedMeshNodeLiveData.postValue(node);
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.SENDING_APP_KEY_ADD);
                }
            } else if (meshMessage instanceof ConfigNetworkTransmitSet) {
                if (mSetupProvisionedNode) {
                    mProvisionedMeshNodeLiveData.postValue(node);
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.SENDING_NETWORK_TRANSMIT_SET);
                }
            }
        }
    }

    @Override
    public void onMeshMessageReceived(final int src, @NonNull final MeshMessage meshMessage) {
        final ProvisionedMeshNode node = mMeshNetwork.getNode(src);
        if (node != null)
            if (meshMessage.getOpCode() == ProxyConfigMessageOpCodes.FILTER_STATUS) {
                mProvisionedMeshNode = node;
                setSelectedMeshNode(node);
                final ProxyConfigFilterStatus status = (ProxyConfigFilterStatus) meshMessage;
                final int unicastAddress = status.getSrc();
                Log.v(TAG, "Proxy configuration source: " + MeshAddress.formatAddress(status.getSrc(), false));
                mConnectedProxyAddress.postValue(unicastAddress);
                mMeshMessageLiveData.postValue(status);
            } else if (meshMessage.getOpCode() == CONFIG_COMPOSITION_DATA_STATUS) {
                if (mSetupProvisionedNode) {
                    mIsCompositionDataReceived = true;
                    mProvisionedMeshNodeLiveData.postValue(node);
                    mConnectedProxyAddress.postValue(node.getUnicastAddress());
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.COMPOSITION_DATA_STATUS_RECEIVED);
                    mHandler.postDelayed(() -> {
                        final ConfigDefaultTtlGet configDefaultTtlGet = new ConfigDefaultTtlGet();
                        mMeshManagerApi.createMeshPdu(node.getUnicastAddress(), configDefaultTtlGet);
                    }, 500);
                } else {
                    updateNode(node);
                }
            } else if (meshMessage.getOpCode() == CONFIG_DEFAULT_TTL_STATUS) {
                final ConfigDefaultTtlStatus status = (ConfigDefaultTtlStatus) meshMessage;
                if (mSetupProvisionedNode) {
                    mIsDefaultTtlReceived = true;
                    mProvisionedMeshNodeLiveData.postValue(node);
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.DEFAULT_TTL_STATUS_RECEIVED);
                    mHandler.postDelayed(() -> {
                        final ConfigNetworkTransmitSet networkTransmitSet = new ConfigNetworkTransmitSet(2, 1);
                        mMeshManagerApi.createMeshPdu(node.getUnicastAddress(), networkTransmitSet);
                    }, 1500);
                } else {
                    updateNode(node);
                    mMeshMessageLiveData.postValue(status);
                }
            } else if (meshMessage.getOpCode() == CONFIG_NETWORK_TRANSMIT_STATUS) {
                if (mSetupProvisionedNode) {
                    mIsNetworkRetransmitSetCompleted = true;
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.NETWORK_TRANSMIT_STATUS_RECEIVED);
                    final ApplicationKey appKey = mMeshNetworkLiveData.getSelectedAppKey();
                    if (appKey != null) {
                        mHandler.postDelayed(() -> {
                            // We should use the app key's boundNetKeyIndex as the network key index when adding the default app key
                            final NetworkKey networkKey = mMeshNetwork.getNetKeys().get(appKey.getBoundNetKeyIndex());
                            final ConfigAppKeyAdd configAppKeyAdd = new ConfigAppKeyAdd(networkKey, appKey);
                            mMeshManagerApi.createMeshPdu(node.getUnicastAddress(), configAppKeyAdd);
                        }, 1500);
                    } else {
                        mSetupProvisionedNode = false;
                        mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.APP_KEY_STATUS_RECEIVED);
                    }
                } else {
                    updateNode(node);
                    final ConfigNetworkTransmitStatus status = (ConfigNetworkTransmitStatus) meshMessage;
                    mMeshMessageLiveData.postValue(status);
                }
            } else if (meshMessage.getOpCode() == CONFIG_APPKEY_STATUS) {
                final ConfigAppKeyStatus status = (ConfigAppKeyStatus) meshMessage;
                mIsFFanInitializing = false;
                if (mSetupProvisionedNode) {
                    mSetupProvisionedNode = false;
                    if (status.isSuccessful()) {
                        mIsAppKeyAddCompleted = true;
                        mProvisionedMeshNodeLiveData.postValue(node);

                        mIsFFanInitializing = true;

                        for (Element element : node.getElements().values()) {
                            final int elementAddress = element.getElementAddress();
                            for (MeshModel model : element.getMeshModels().values()) {
                                final int modelId = model.getModelId();
                                if (modelId == 0x05D60001) {
                                    final ConfigModelAppBind bind = new ConfigModelAppBind(elementAddress, modelId, 0);
                                    mMeshManagerApi.createMeshPdu(node.getUnicastAddress(), bind);
                                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.SENDING_CONFIG_MODEL_APP_STATUS);
                                }
                            }
                        }

                    }
                    mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.APP_KEY_STATUS_RECEIVED);
                } else {
                    updateNode(node);
                    mMeshMessageLiveData.postValue(status);
                }
            } else if (meshMessage.getOpCode() == CONFIG_MODEL_APP_STATUS) {
                if (updateNode(node)) {
                    final ConfigModelAppStatus status = (ConfigModelAppStatus) meshMessage;
                    final Element element = node.getElements().get(status.getElementAddress());
                    if (node.getElements().containsKey(status.getElementAddress())) {
                        mSelectedElement.postValue(element);
                        final MeshModel model = element.getMeshModels().get(status.getModelIdentifier());
                        mSelectedModel.postValue(model);

                        if (mIsFFanInitializing && status.getModelIdentifier() == 0x05D60001) {
                            mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.CONFIG_MODEL_APP_STATUS_RECEIVED);
                            if (status.isSuccessful()) {
                                MeshMessage publication = new ConfigModelPublicationSet(element.getElementAddress(),
                                        0xc000,
                                        0,
                                        false,
                                        255,
                                        0,
                                        0,
                                        1,
                                        1,
                                        0x05D60001);

                                mMeshManagerApi.createMeshPdu(node.getUnicastAddress(), publication);
                                mHandler.postDelayed(() -> mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.SENDING_CONFIG_MODEL_PUBLICATION_STATUS), 100);
                            } else {
                                mHandler.postDelayed(() -> mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.CONFIG_FFAN_MODEL_FAILED_RECEIVED), 100);
                            }
                        }
                    }
                }
            } else if (meshMessage.getOpCode() == CONFIG_MODEL_PUBLICATION_STATUS) {
                if (updateNode(node)) {
                    final ConfigModelPublicationStatus status = (ConfigModelPublicationStatus) meshMessage;
                    if (node.getElements().containsKey(status.getElementAddress())) {
                        final Element element = node.getElements().get(status.getElementAddress());
                        mSelectedElement.postValue(element);
                        final MeshModel model = element.getMeshModels().get(status.getModelIdentifier());
                        mSelectedModel.postValue(model);

                        if (mIsFFanInitializing && status.getModelIdentifier() == 0x05D60001) {
                            if (status.isSuccessful()) {
                                mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.CONFIG_MODEL_PUBLICATION_STATUS_RECEIVED);
                                String nodeType = node.getUuid().substring(16, 18);
                                if (nodeType.equals("03")) {
                                    mIsFFanInitializing = false;
                                    mHandler.postDelayed(() -> mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.CONFIG_FFAN_MODEL_SUCCESS_RECEIVED), 100);
                                } else {
                                    MeshMessage subscription = new ConfigModelSubscriptionAdd(element.getElementAddress(), 0xc000, 0x05D60001);
                                    mMeshManagerApi.createMeshPdu(node.getUnicastAddress(), subscription);
                                    mHandler.postDelayed(() -> mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.SENDING_CONFIG_MODEL_SUBSCRIPTION_STATUS), 100);
                                }
                            } else {
                                mHandler.postDelayed(() -> mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.CONFIG_FFAN_MODEL_FAILED_RECEIVED), 100);
                            }
                        }
                    }
                }

            } else if (meshMessage.getOpCode() == CONFIG_MODEL_SUBSCRIPTION_STATUS) {
                if (updateNode(node)) {
                    final ConfigModelSubscriptionStatus status = (ConfigModelSubscriptionStatus) meshMessage;
                    if (node.getElements().containsKey(status.getElementAddress())) {
                        final Element element = node.getElements().get(status.getElementAddress());
                        mSelectedElement.postValue(element);
                        final MeshModel model = element.getMeshModels().get(status.getModelIdentifier());
                        mSelectedModel.postValue(model);
                        if (mIsFFanInitializing && status.getModelIdentifier() == 0x05D60001) {
                            mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.CONFIG_MODEL_SUBSCRIPTION_STATUS_RECEIVED);
                            if (status.isSuccessful()) {
                                mHandler.postDelayed(() -> mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.CONFIG_FFAN_MODEL_SUCCESS_RECEIVED), 100);
                            } else {
                                mHandler.postDelayed(() -> mProvisioningStateLiveData.onMeshNodeStateUpdated(ProvisionerStates.CONFIG_FFAN_MODEL_FAILED_RECEIVED), 100);
                            }
                        }
                    }
                    mIsFFanInitializing = false;
                }

            } else if (meshMessage.getOpCode() == CONFIG_NODE_RESET_STATUS) {
                mBleMeshManager.setClearCacheRequired();
                final ConfigNodeResetStatus status = (ConfigNodeResetStatus) meshMessage;
                mExtendedMeshNode.postValue(null);
                loadNodes();
                mMeshMessageLiveData.postValue(status);

            } else if (meshMessage.getOpCode() == CONFIG_RELAY_STATUS) {
                if (updateNode(node)) {
                    final ConfigRelayStatus status = (ConfigRelayStatus) meshMessage;
                    mMeshMessageLiveData.postValue(status);
                }
            } else if (meshMessage.getOpCode() == CONFIG_HEARTBEAT_PUBLICATION_STATUS) {
                if (updateNode(node)) {
                    final Element element = node.getElements().get(meshMessage.getSrc());
                    final MeshModel model = element.getMeshModels().get((int) SigModelParser.CONFIGURATION_SERVER);
                    mSelectedModel.postValue(model);
                    mMeshMessageLiveData.postValue(meshMessage);
                }
            } else if (meshMessage.getOpCode() == CONFIG_HEARTBEAT_SUBSCRIPTION_STATUS) {
                if (updateNode(node)) {
                    final Element element = node.getElements().get(meshMessage.getSrc());
                    final MeshModel model = element.getMeshModels().get((int) SigModelParser.CONFIGURATION_SERVER);
                    mSelectedModel.postValue(model);
                    mMeshMessageLiveData.postValue(meshMessage);
                }
            } else if (meshMessage.getOpCode() == CONFIG_GATT_PROXY_STATUS) {
                if (updateNode(node)) {
                    final ConfigGattProxyStatus status = (ConfigGattProxyStatus) meshMessage;
                    mMeshMessageLiveData.postValue(status);
                }
            } else if (meshMessage.getOpCode() == GENERIC_ON_OFF_STATUS) {
                if (updateNode(node)) {
                    final GenericOnOffStatus status = (GenericOnOffStatus) meshMessage;
                    if (node.getElements().containsKey(status.getSrcAddress())) {
                        final Element element = node.getElements().get(status.getSrcAddress());
                        mSelectedElement.postValue(element);
                        final MeshModel model = element.getMeshModels().get((int) SigModelParser.GENERIC_ON_OFF_SERVER);
                        mSelectedModel.postValue(model);
                    }
                }
            } else if (meshMessage.getOpCode() == GENERIC_LEVEL_STATUS) {
                if (updateNode(node)) {
                    final GenericLevelStatus status = (GenericLevelStatus) meshMessage;
                    if (node.getElements().containsKey(status.getSrcAddress())) {
                        final Element element = node.getElements().get(status.getSrcAddress());
                        mSelectedElement.postValue(element);
                        final MeshModel model = element.getMeshModels().get((int) SigModelParser.GENERIC_LEVEL_SERVER);
                        mSelectedModel.postValue(model);
                    }
                }
            } else if (meshMessage.getOpCode() == SCENE_STATUS) {
                if (updateNode(node)) {
                    final SceneStatus status = (SceneStatus) meshMessage;
                    if (node.getElements().containsKey(status.getSrcAddress())) {
                        final Element element = node.getElements().get(status.getSrcAddress());
                        mSelectedElement.postValue(element);
                    }
                }
            } else if (meshMessage.getOpCode() == SCENE_REGISTER_STATUS) {
                if (updateNode(node)) {
                    final SceneRegisterStatus status = (SceneRegisterStatus) meshMessage;
                    if (node.getElements().containsKey(status.getSrcAddress())) {
                        final Element element = node.getElements().get(status.getSrcAddress());
                        mSelectedElement.postValue(element);
                    }
                }
            } else if (meshMessage instanceof VendorModelMessageStatus) {
                if (updateNode(node)) {
                    final VendorModelMessageStatus status = (VendorModelMessageStatus) meshMessage;
                    if (node.getElements().containsKey(status.getSrcAddress())) {
                        final Element element = node.getElements().get(status.getSrcAddress());
                        mSelectedElement.postValue(element);
                        final MeshModel model = element.getMeshModels().get(status.getModelIdentifier());
                        mSelectedModel.postValue(model);
                    }
                }
            }

        if (mMeshMessageLiveData.hasActiveObservers()) {
            mMeshMessageLiveData.postValue(meshMessage);
        }

        //Refresh mesh network live data
        if (mMeshManagerApi.getMeshNetwork() != null) {
            mMeshNetworkLiveData.refresh(mMeshManagerApi.getMeshNetwork());
        }
    }

    @Override
    public void onMessageDecryptionFailed(final String meshLayer, final String errorMessage) {
        Log.e(TAG, "Decryption failed in " + meshLayer + " : " + errorMessage);
    }

    /**
     * Loads the network that was loaded from the db or imported from the mesh cdb
     *
     * @param meshNetwork mesh network that was loaded
     */
    private void loadNetwork(final MeshNetwork meshNetwork) {
        mMeshNetwork = meshNetwork;
        if (mMeshNetwork != null) {

            if (!mMeshNetwork.isProvisionerSelected()) {
                final Provisioner provisioner = meshNetwork.getProvisioners().get(0);
                provisioner.setLastSelected(true);
                mMeshNetwork.selectProvisioner(provisioner);
            }
            //Load live data with mesh network
            mMeshNetworkLiveData.loadNetworkInformation(meshNetwork);
            //Load live data with provisioned nodes
            loadNodes();

            final ProvisionedMeshNode node = getSelectedMeshNode().getValue();
            if (node != null) {
                mExtendedMeshNode.postValue(mMeshNetwork.getNode(node.getUuid()));
            }
        }
    }

    /**
     * We should only update the selected node, since sending messages to group address will notify with nodes that is not on the UI
     */
    private boolean updateNode(@NonNull final ProvisionedMeshNode node) {
        if (mProvisionedMeshNode != null && mProvisionedMeshNode.getUnicastAddress() == node.getUnicastAddress()) {
            mProvisionedMeshNode = node;
            mExtendedMeshNode.postValue(node);
            return true;
        }
        return false;
    }

    /**
     * Starts reconnecting to the device
     */
    private void startScan() {
        if (mIsScanning)
            return;

        mIsScanning = true;
        // Scanning settings
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                // Refresh the devices list every second
                .setReportDelay(0)
                // Hardware filtering has some issues on selected devices
                .setUseHardwareFilteringIfSupported(false)
                // Samsung S6 and S6 Edge report equal value of RSSI for all devices. In this app we ignore the RSSI.
                /*.setUseHardwareBatchingIfSupported(false)*/
                .build();

        // Let's use the filter to scan only for Mesh devices
        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid((MESH_PROXY_UUID))).build());

        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        scanner.startScan(filters, settings, scanCallback);
        Log.d(TAG, "Scan started");
        mHandler.postDelayed(mScannerTimeout, 20000);
    }

    /**
     * stop scanning for bluetooth devices.
     */
    private void stopScan() {
        mHandler.removeCallbacks(mScannerTimeout);
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        scanner.stopScan(scanCallback);
        mIsScanning = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            //In order to connectToProxy to the correct device, the hash advertised in the advertisement data should be matched.
            //This is to make sure we connectToProxy to the same device as device addresses could change after provisioning.
            final ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord != null) {
                final byte[] serviceData = Utils.getServiceData(result, MESH_PROXY_UUID);
                if (serviceData != null) {
                    if (mMeshManagerApi.isAdvertisedWithNodeIdentity(serviceData)) {
                        final ProvisionedMeshNode node = mProvisionedMeshNode;
                        if (mMeshManagerApi.nodeIdentityMatches(node, serviceData)) {
                            Log.d(TAG, "onScanResult, Scan stopped!");
                            stopScan();
                            // mConnectionState.postValue("Provisioned node found");
                            mConnectionState.postValue(mMeshManagerApi.getContext().getString(R.string.state_found));
                            onProvisionedDeviceFound(node, new ExtendedBluetoothDevice(result));
                        }
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(@NonNull final List<ScanResult> results) {
            // Batch scan is disabled (report delay = 0)
        }

        @Override
        public void onScanFailed(final int errorCode) {
            Log.d(TAG, "Scan Failed: " + errorCode);
        }
    };

    private void onProvisionedDeviceFound(final ProvisionedMeshNode node, final ExtendedBluetoothDevice device) {
        Log.d(TAG, "onProvisionedDeviceFound: " + node.getUuid() + "[" + device.getAddress() + "]");
        mSetupProvisionedNode = true;
        mProvisionedMeshNode = node;
        mIsReconnectingFlag = true;
        //Added an extra delay to ensure reconnection
        mHandler.postDelayed(() -> connectToProxy(device), 2000);
    }

    /**
     * Generates the groups based on the addresses each models have subscribed to
     */
    private void loadGroups1() {
        mGroups.postValue(mMeshNetwork.getGroups());
    }

    private void updateSelectedGroup() {
        final Group selectedGroup = mSelectedGroupLiveData.getValue();
        if (selectedGroup != null) {
            mSelectedGroupLiveData.postValue(mMeshNetwork.getGroup(selectedGroup.getAddress()));
        }
    }

    /**
     * Sets the group that was selected from the GroupAdapter.
     */
    void setSelectedGroup(final int address) {
        final Group group = mMeshNetwork.getGroup(address);
        if (group != null) {
            mSelectedGroupLiveData.postValue(group);
        }
    }
}
