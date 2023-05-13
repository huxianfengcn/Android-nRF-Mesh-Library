/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.nrfmesh.viewmodels;

import androidx.lifecycle.LiveData;

import java.util.ArrayList;

import no.nordicsemi.android.nrfmesh.R;
import no.nordicsemi.android.nrfmesh.utils.ProvisionerStates;

public class ProvisioningStatusLiveData extends LiveData<ProvisioningStatusLiveData> {

    private final ArrayList<ProvisionerProgress> mProgressList = new ArrayList<>();

    public void clear() {
        mProgressList.clear();
        postValue(this);
    }

    public ArrayList<ProvisionerProgress> getStateList() {
        return mProgressList;
    }


    public ProvisionerProgress getProvisionerProgress() {
        if (mProgressList.size() == 0)
            return null;
        return mProgressList.get(mProgressList.size() - 1);
    }

    public void onMeshNodeStateUpdated(final ProvisionerStates state) {
        final ProvisionerProgress provisioningProgress;
        switch (state) {
            case PROVISIONING_INVITE:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_invite, true);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_CAPABILITIES:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_capabilities, false);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_START:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_start, true);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_PUBLIC_KEY_SENT:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_public_key_sent, true);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_PUBLIC_KEY_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_public_key_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_AUTHENTICATION_STATIC_OOB_WAITING:
            case PROVISIONING_AUTHENTICATION_OUTPUT_OOB_WAITING:
            case PROVISIONING_AUTHENTICATION_INPUT_OOB_WAITING:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_waiting_oob, true);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_AUTHENTICATION_INPUT_ENTERED:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_authentication_input_entered, true);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_INPUT_COMPLETE:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_input_complete, false);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_CONFIRMATION_SENT:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_confirmation_sent, true);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_CONFIRMATION_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_confirmation_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_RANDOM_SENT:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_random_sent, true);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_RANDOM_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_random_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_DATA_SENT:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_data_sent, true);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_COMPLETE:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_complete, false);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONING_FAILED:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioning_failed, false);
                mProgressList.add(provisioningProgress);
            default:
                break;
            case COMPOSITION_DATA_GET_SENT:
                provisioningProgress = new ProvisionerProgress(state, R.string.composition_data_get_sent, true);
                mProgressList.add(provisioningProgress);
                break;
            case COMPOSITION_DATA_STATUS_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.composition_data_status_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case SENDING_DEFAULT_TTL_GET:
                provisioningProgress = new ProvisionerProgress(state, R.string.sending_default_ttl_get, true);
                mProgressList.add(provisioningProgress);
                break;
            case DEFAULT_TTL_STATUS_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.default_ttl_status_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case SENDING_APP_KEY_ADD:
                provisioningProgress = new ProvisionerProgress(state, R.string.sending_app_key_add, true);
                mProgressList.add(provisioningProgress);
                break;
            case APP_KEY_STATUS_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.app_key_status_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case SENDING_NETWORK_TRANSMIT_SET:
                provisioningProgress = new ProvisionerProgress(state, R.string.sending_network_transmit_set, true);
                mProgressList.add(provisioningProgress);
                break;
            case NETWORK_TRANSMIT_STATUS_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.network_transmit_status_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case SENDING_CONFIG_MODEL_APP_STATUS:
                provisioningProgress = new ProvisionerProgress(state, R.string.sending_config_model_app_status, false);
                mProgressList.add(provisioningProgress);
                break;
            case CONFIG_MODEL_APP_STATUS_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.config_model_app_status_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case SENDING_CONFIG_MODEL_PUBLICATION_STATUS:
                provisioningProgress = new ProvisionerProgress(state, R.string.sending_config_model_publication_status, false);
                mProgressList.add(provisioningProgress);
                break;
            case CONFIG_MODEL_PUBLICATION_STATUS_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.config_model_publication_status_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case SENDING_CONFIG_MODEL_SUBSCRIPTION_STATUS:
                provisioningProgress = new ProvisionerProgress(state, R.string.sending_config_model_subscription_status, false);
                mProgressList.add(provisioningProgress);
                break;
            case CONFIG_MODEL_SUBSCRIPTION_STATUS_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.config_model_subscription_status_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case CONFIG_FFAN_MODEL_SUCCESS_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.config_ffan_model_success_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case CONFIG_FFAN_MODEL_FAILED_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.config_ffan_model_failed_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case SENDING_BLOCK_ACKNOWLEDGEMENT:
                provisioningProgress = new ProvisionerProgress(state, R.string.sending_block_acknowledgement, true);
                mProgressList.add(provisioningProgress);
                break;
            case BLOCK_ACKNOWLEDGEMENT_RECEIVED:
                provisioningProgress = new ProvisionerProgress(state, R.string.block_acknowledgement_received, false);
                mProgressList.add(provisioningProgress);
                break;
            case PROVISIONER_UNASSIGNED:
                provisioningProgress = new ProvisionerProgress(state, R.string.provisioner_unassigned, false);
                mProgressList.add(provisioningProgress);
                break;
        }
        postValue(this);
    }
}
