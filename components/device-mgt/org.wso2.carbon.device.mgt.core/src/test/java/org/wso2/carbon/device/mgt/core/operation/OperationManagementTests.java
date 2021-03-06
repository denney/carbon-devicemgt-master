/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/

package org.wso2.carbon.device.mgt.core.operation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.device.mgt.common.Device;
import org.wso2.carbon.device.mgt.common.DeviceIdentifier;
import org.wso2.carbon.device.mgt.common.DeviceManagementException;
import org.wso2.carbon.device.mgt.common.InvalidDeviceException;
import org.wso2.carbon.device.mgt.common.PaginationRequest;
import org.wso2.carbon.device.mgt.common.PaginationResult;
import org.wso2.carbon.device.mgt.common.operation.mgt.Activity;
import org.wso2.carbon.device.mgt.common.operation.mgt.ActivityStatus;
import org.wso2.carbon.device.mgt.common.operation.mgt.Operation;
import org.wso2.carbon.device.mgt.common.operation.mgt.OperationManagementException;
import org.wso2.carbon.device.mgt.common.operation.mgt.OperationManager;
import org.wso2.carbon.device.mgt.core.TestDeviceManagementService;
import org.wso2.carbon.device.mgt.core.authorization.DeviceAccessAuthorizationServiceImpl;
import org.wso2.carbon.device.mgt.core.common.BaseDeviceManagementTest;
import org.wso2.carbon.device.mgt.core.common.TestDataHolder;
import org.wso2.carbon.device.mgt.core.config.DeviceConfigurationManager;
import org.wso2.carbon.device.mgt.core.internal.DeviceManagementDataHolder;
import org.wso2.carbon.device.mgt.core.internal.DeviceManagementServiceComponent;
import org.wso2.carbon.device.mgt.core.operation.mgt.CommandOperation;
import org.wso2.carbon.device.mgt.core.operation.mgt.ConfigOperation;
import org.wso2.carbon.device.mgt.core.operation.mgt.OperationManagerImpl;
import org.wso2.carbon.device.mgt.core.operation.mgt.PolicyOperation;
import org.wso2.carbon.device.mgt.core.operation.mgt.ProfileOperation;
import org.wso2.carbon.device.mgt.core.service.DeviceManagementProviderService;
import org.wso2.carbon.device.mgt.core.service.DeviceManagementProviderServiceImpl;
import org.wso2.carbon.device.mgt.core.service.GroupManagementProviderServiceImpl;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.internal.RegistryDataHolder;
import org.wso2.carbon.registry.core.jdbc.realm.InMemoryRealmService;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OperationManagementTests{
    private static final Log log = LogFactory.getLog(OperationManagementTests.class);

    private static final String DEVICE_TYPE = "OP_TEST_TYPE";
    private static final String DEVICE_ID_PREFIX = "OP-TEST-DEVICE-ID-";
    private static final String COMMAND_OPERATON_CODE = "COMMAND-TEST";
    private static final String POLICY_OPERATION_CODE = "POLICY-TEST";
    private static final String CONFIG_OPERATION_CODE = "CONFIG-TEST";
    private static final String PROFILE_OPERATION_CODE = "PROFILE-TEST";
    private static final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
    private static final int NO_OF_DEVICES = 5;
    private static final String ADMIN_USER = "admin";
    private static final String NON_ADMIN_USER = "test";

    private List<DeviceIdentifier> deviceIds = new ArrayList<>();
    private OperationManager operationMgtService;

    @BeforeClass
    public void init() throws Exception {
        DeviceConfigurationManager.getInstance().initConfig();
        log.info("Initializing");
        for (int i = 0; i < NO_OF_DEVICES; i++) {
            deviceIds.add(new DeviceIdentifier(DEVICE_ID_PREFIX + i, DEVICE_TYPE));
        }
        List<Device> devices = TestDataHolder.generateDummyDeviceData(this.deviceIds);
        DeviceManagementProviderService deviceMgtService = new DeviceManagementProviderServiceImpl();
        DeviceManagementServiceComponent.notifyStartupListeners();
        DeviceManagementDataHolder.getInstance().setDeviceManagementProvider(deviceMgtService);
        DeviceManagementDataHolder.getInstance().setRegistryService(getRegistryService());
        DeviceManagementDataHolder.getInstance().setDeviceAccessAuthorizationService(new DeviceAccessAuthorizationServiceImpl());
        DeviceManagementDataHolder.getInstance().setGroupManagementProviderService(new GroupManagementProviderServiceImpl());
        DeviceManagementDataHolder.getInstance().setDeviceTaskManagerService(null);
        deviceMgtService.registerDeviceType(new TestDeviceManagementService(DEVICE_TYPE,
                MultitenantConstants.SUPER_TENANT_DOMAIN_NAME));
        for (Device device : devices) {
            deviceMgtService.enrollDevice(device);
        }
        List<Device> returnedDevices = deviceMgtService.getAllDevices(DEVICE_TYPE);
        for (Device device : returnedDevices) {
            if (!device.getDeviceIdentifier().startsWith(DEVICE_ID_PREFIX)) {
                throw new Exception("Incorrect device with ID - " + device.getDeviceIdentifier() + " returned!");
            }
        }
        this.operationMgtService = new OperationManagerImpl(DEVICE_TYPE);
    }

    private RegistryService getRegistryService() throws RegistryException {
        RealmService realmService = new InMemoryRealmService();
        RegistryDataHolder.getInstance().setRealmService(realmService);
        DeviceManagementDataHolder.getInstance().setRealmService(realmService);
        InputStream is = this.getClass().getClassLoader().getResourceAsStream("carbon-home/repository/conf/registry.xml");
        RegistryContext context = RegistryContext.getBaseInstance(is, realmService);
        context.setSetup(true);
        return context.getEmbeddedRegistryService();
    }

    @Test
    public void addCommandOperation() throws DeviceManagementException, OperationManagementException, InvalidDeviceException {
        Activity activity = this.operationMgtService.addOperation(getOperation(new CommandOperation(), Operation.Type.COMMAND, COMMAND_OPERATON_CODE),
                this.deviceIds);
        validateOperationResponse(activity);
    }

    @Test(dependsOnMethods = "addCommandOperation")
    public void addPolicyOperation() throws DeviceManagementException, OperationManagementException, InvalidDeviceException {
        Activity activity = this.operationMgtService.addOperation(getOperation(new PolicyOperation(), Operation.Type.POLICY, POLICY_OPERATION_CODE),
                this.deviceIds);
        validateOperationResponse(activity);
    }

    @Test(dependsOnMethods = "addPolicyOperation")
    public void addConfigOperation() throws DeviceManagementException, OperationManagementException, InvalidDeviceException {
        Activity activity = this.operationMgtService.addOperation(getOperation(new ConfigOperation(), Operation.Type.CONFIG, CONFIG_OPERATION_CODE),
                this.deviceIds);
        validateOperationResponse(activity);
    }

    @Test(dependsOnMethods = "addConfigOperation")
    public void addProfileOperation() throws DeviceManagementException, OperationManagementException, InvalidDeviceException {
        Activity activity = this.operationMgtService.addOperation(getOperation(new ProfileOperation(), Operation.Type.PROFILE, PROFILE_OPERATION_CODE),
                this.deviceIds);
        validateOperationResponse(activity);
    }

    private Operation getOperation(Operation operation, Operation.Type type, String code) {
        String date = new SimpleDateFormat(DATE_FORMAT_NOW).format(new Date());
        operation.setCreatedTimeStamp(date);
        operation.setType(type);
        operation.setCode(code);
        return operation;
    }

    private void validateOperationResponse(Activity activity) {
        Assert.assertEquals(activity.getActivityStatus().size(), NO_OF_DEVICES, "The operation reponse for add operation only have - " +
                activity.getActivityStatus().size());
        for (ActivityStatus status : activity.getActivityStatus()) {
            Assert.assertEquals(status.getStatus(), ActivityStatus.Status.PENDING);
        }
    }

    @Test(dependsOnMethods = "addProfileOperation")
    public void getOperations() throws DeviceManagementException, OperationManagementException, InvalidDeviceException {
        for (DeviceIdentifier deviceIdentifier : deviceIds) {
            List operations = this.operationMgtService.getOperations(deviceIdentifier);
            Assert.assertEquals(operations.size(), 4, "The operations should be 4, but found only " + operations.size());
        }
    }

    @Test(dependsOnMethods = "getOperations")
    public void getPendingOperations() throws DeviceManagementException, OperationManagementException, InvalidDeviceException {
        for (DeviceIdentifier deviceIdentifier : deviceIds) {
            List operations = this.operationMgtService.getPendingOperations(deviceIdentifier);
            Assert.assertEquals(operations.size(), 4, "The pending operations should be 4, but found only " + operations.size());
        }
    }

    @Test(dependsOnMethods = "getPendingOperations")
    public void getPaginatedRequestAsAdmin() throws OperationManagementException {
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(MultitenantConstants.SUPER_TENANT_ID, true);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(ADMIN_USER);
        PaginationRequest request = new PaginationRequest(1, 2);
        request.setDeviceType(DEVICE_TYPE);
        request.setOwner(ADMIN_USER);
        for (DeviceIdentifier deviceIdentifier : deviceIds) {
            PaginationResult result = this.operationMgtService.getOperations(deviceIdentifier, request);
            Assert.assertEquals(result.getRecordsFiltered(), 4);
            Assert.assertEquals(result.getData().size(), 2);
            Assert.assertEquals(result.getRecordsTotal(), 4);
        }
        PrivilegedCarbonContext.endTenantFlow();
    }

    @Test(dependsOnMethods = "getPendingOperations")
    public void getPaginatedRequestAsNonAdmin() throws OperationManagementException {
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(MultitenantConstants.SUPER_TENANT_ID, true);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(NON_ADMIN_USER);
        PaginationRequest request = new PaginationRequest(1, 2);
        request.setDeviceType(DEVICE_TYPE);
        request.setOwner(ADMIN_USER);
        for (DeviceIdentifier deviceIdentifier : deviceIds) {
            try {
                this.operationMgtService.getOperations(deviceIdentifier, request);
            } catch (OperationManagementException ex) {
                if (ex.getMessage() == null){
                    Assert.assertTrue(ex.getMessage().contains("User '" + NON_ADMIN_USER + "' is not authorized"));
                }
            }
        }
        PrivilegedCarbonContext.endTenantFlow();
    }

    @Test(dependsOnMethods = "getPaginatedRequestAsAdmin")
    public void updateOperation() throws OperationManagementException {
        DeviceIdentifier deviceIdentifier = this.deviceIds.get(0);
        List operations = this.operationMgtService.getPendingOperations(deviceIdentifier);
        Assert.assertTrue(operations!= null && operations.size()==4);
        Operation operation = (Operation) operations.get(0);
        operation.setStatus(Operation.Status.COMPLETED);
        operation.setOperationResponse("The operation is successfully completed");
        this.operationMgtService.updateOperation(deviceIdentifier, operation);
        List pendingOperations = this.operationMgtService.getPendingOperations(deviceIdentifier);
        Assert.assertEquals(pendingOperations.size(), 3);
    }
}
