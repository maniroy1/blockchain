/*
 *  Copyright 2016, 2017 DTCC, Fujitsu Australia Software Technology, IBM - All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.moglix.reports.fabric.sdkintegration;

import static com.moglix.reports.fabric.sdk.testutils.TestUtils.resetConfig;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.sdk.BlockInfo.EnvelopeType.TRANSACTION_ENVELOPE;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.BlockchainInfo;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.ChannelConfiguration;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstallProposalRequest;
import org.hyperledger.fabric.sdk.InstantiateProposalRequest;
import org.hyperledger.fabric.sdk.Orderer;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;
import org.hyperledger.fabric.sdk.QueryByChaincodeRequest;
import org.hyperledger.fabric.sdk.SDKUtils;
import org.hyperledger.fabric.sdk.TransactionInfo;
import org.hyperledger.fabric.sdk.TransactionProposalRequest;
import org.hyperledger.fabric.sdk.TxReadWriteSetInfo;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.InvalidProtocolBufferRuntimeException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;

import com.moglix.reports.fabric.sdk.TestConfigHelper;
import com.moglix.reports.fabric.sdk.testutils.TestConfig;

/**
 * Test end to end scenario
 */
public class End2endIT {

    private static final TestConfig testConfig = TestConfig.getConfig();
    private static final String TEST_ADMIN_NAME = "admin";
    private static final String TESTUSER_1_NAME = "user1";
    private static final String TEST_FIXTURES_PATH = "src/main/java/com/moglix/reports/fixture";

    private static final String CHAIN_CODE_NAME = "example_cc_go";
    private static final String CHAIN_CODE_PATH = "github.com/example_cc";
    private static final String CHAIN_CODE_VERSION = "34";

    private static final String FOO_CHANNEL_NAME = "foo";
    private static final String BAR_CHANNEL_NAME = "bar";

    private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);
    private static final String EXPECTED_EVENT_NAME = "event";

    String testTxID = null;  // save the CC invoke TxID and use in queries

    private final TestConfigHelper configHelper = new TestConfigHelper();

    private Collection<SampleOrg> testSampleOrgs;
    
    
    File sampleStoreFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");
    final SampleStore sampleStore = new SampleStore(sampleStoreFile);
    
    
    public static void main(String args[]) {
    	End2endIT e = new End2endIT();
    	List<String> resultTransactionList = new ArrayList<>();
    	String data = "{\"agreementNo\": \"6500002197\", \"agreementType\": \"SA\", \"docType\": \"Scheduling Agreement\" }";
    
    	try {
			e.checkConfig();
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException
				| MalformedURLException | org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	resultTransactionList = e.setup(data);
    }
    
    public List<String> tranaction(String data) {
    	
    	try {
			checkConfig();
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException
				| MalformedURLException | org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	return setup(data);
    	
    }
    
    public void checkConfig() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, MalformedURLException, org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException {
        out("\n\n\nRUNNING: End2endIT.\n");
        //   configHelper.clearConfig();
        //   assertEquals(256, Config.getConfig().getSecurityLevel());
        resetConfig();
        configHelper.customizeConfig();

        testSampleOrgs = testConfig.getIntegrationTestsSampleOrgs();
        
        System.out.println("TestSampleOrgs size : " + testSampleOrgs.size());
        //Set up hfca for each sample org

        for (SampleOrg sampleOrg : testSampleOrgs) {
            String caName = sampleOrg.getCAName(); //Try one of each name and no name.
            System.out.println("caName : " + caName + "ca location : " + sampleOrg.getCALocation());
            if (caName != null && !caName.isEmpty()) {
                sampleOrg.setCAClient(HFCAClient.createNewInstance(caName, sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            } else {
                sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            }
        }
    }

    public List<String> setup(String data) {
    	List<String> res = new ArrayList<>();
        try {

            ////////////////////////////
            // Setup client

            //Create instance of client.
            HFClient client = HFClient.createNewInstance();

            client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

            // client.setMemberServices(peerOrg1FabricCA);

            ////////////////////////////
            //Set up USERS

            //Persistence is not part of SDK. Sample file store is for demonstration purposes only!
            //   MUST be replaced with more robust application implementation  (Database, LDAP)
            

            if (sampleStoreFile.exists()) { //For testing start fresh
                sampleStoreFile.delete();
            }        
            //  sampleStoreFile.deleteOnExit();

            //SampleUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface

            ////////////////////////////
            // get users for all orgs
            
            out("testSampleOrgs size : %s ", testSampleOrgs.size());

            for (SampleOrg sampleOrg : testSampleOrgs) {

                HFCAClient ca = sampleOrg.getCAClient();

                final String orgName = sampleOrg.getName();
                final String mspid = sampleOrg.getMSPID();
                
                System.out.println("orgname and mspId :  " + orgName + " - " + mspid);
                
                ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

                HFCAInfo info = ca.info(); //just check if we connect at all.
              //  assertNotNull(info);
                out("HFCAINFO : %s ", info.getCACertificateChain());
                String infoName = info.getCAName();
//                if (infoName != null && !infoName.isEmpty()) {
//                    assertEquals(ca.getCAName(), infoName);
//                }
                
                out("CA NAME : %s , infoName : %s ", ca.getCAName(), infoName);

                SampleUser admin = sampleStore.getMember(TEST_ADMIN_NAME, orgName);
             
                if (!admin.isEnrolled()) {  //Preregistered admin only needs to be enrolled with Fabric caClient.
                    admin.setEnrollment(ca.enroll(admin.getName(), "adminpw"));
                    admin.setMspId(mspid);
                }

                sampleOrg.setAdmin(admin); // The admin of this org --

                SampleUser user = sampleStore.getMember(TESTUSER_1_NAME, sampleOrg.getName());
                if (!user.isRegistered()) {  // users need to be registered AND enrolled
                    RegistrationRequest rr = new RegistrationRequest(user.getName(), "org1.department1");
                    user.setEnrollmentSecret(ca.register(rr, admin));
                }
                
                if (!user.isEnrolled()) {
                    user.setEnrollment(ca.enroll(user.getName(), user.getEnrollmentSecret()));
                    user.setMspId(mspid);
                }
                sampleOrg.addUser(user); //Remember user belongs to this Org

                final String sampleOrgName = sampleOrg.getName();
                final String sampleOrgDomainName = sampleOrg.getDomainName();
                
                out("------------- sample org name : %s ", sampleOrgName);
                out("------------- sample org domain name : %s ", sampleOrgDomainName);

                // src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/

                SampleUser peerOrgAdmin = sampleStore.getMember(sampleOrgName + "Admin", sampleOrgName, sampleOrg.getMSPID(),
                        Util.findFileSk(Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/",
                                sampleOrgDomainName, format("/users/Admin@%s/msp/keystore", sampleOrgDomainName)).toFile()),
                        Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/", sampleOrgDomainName,
                                format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", sampleOrgDomainName, sampleOrgDomainName)).toFile());
                
                out("111111111111111111111111111111  : %s", peerOrgAdmin.getName());

                sampleOrg.setPeerAdmin(peerOrgAdmin); //A special user that can create channels, join peers and install chaincode

            }

            ////////////////////////////
            //Construct and run the channels
            SampleOrg sampleOrg = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
            
            out("XXXXXXXXXXXX sampleOrg name : %s , sampleOrg CANAME : %s, ", sampleOrg.name, sampleOrg.getCAName() );
            
            Set<String> peernames = sampleOrg.getPeerNames();
            Iterator<String> itr = peernames.iterator();
			while(itr.hasNext()){
				out("peer name : %s ", itr.next());
			}
            
            Channel fooChannel = constructChannel(FOO_CHANNEL_NAME, client, sampleOrg);
            
            res =  runChannel(client, fooChannel, true, sampleOrg, data);
            
            fooChannel.shutdown(true); // Force foo channel to shutdown clean up resources.
            //assertNull(client.getChannel(FOO_CHANNEL_NAME));
            out("\n");

//            sampleOrg = testConfig.getIntegrationTestsSampleOrg("peerOrg2");
//            
//            out("YYYYYYYYYY sampleOrg name : %s , sampleOrg CANAME : %s, ", sampleOrg.name, sampleOrg.getCAName() );
//            
//            Set<String> peernames2 = sampleOrg.getPeerNames();
//            Iterator<String> itr2 = peernames2.iterator();
//			while(itr2.hasNext()){
//				out("peer name : %s ", itr2.next());
//			}
//			
//			Channel barChannel = constructChannel(BAR_CHANNEL_NAME, client, sampleOrg);
//            /**
//             * sampleStore.saveChannel uses {@link Channel#serializeChannel()}
//             */
//            sampleStore.saveChannel(barChannel);
//            runChannel(client, barChannel, true, sampleOrg, 100); //run a newly constructed bar channel with different b value!
//            //let bar channel just shutdown so we have both scenarios.
//
//            out("\nTraverse the blocks for chain %s ", barChannel.getName());
//            blockWalker(client, barChannel);
            out("That's all folks!");

        } catch (Exception e) {
            e.printStackTrace();

            //fail(e.getMessage());
        }
        
        return res;

    }

    //CHECKSTYLE.OFF: Method length is 320 lines (max allowed is 150).
    List<String> runChannel(HFClient client, Channel channel, boolean installChaincode, SampleOrg sampleOrg, String data) {

        class ChaincodeEventCapture { //A test class to capture chaincode events
            final String handle;
            final BlockEvent blockEvent;
            final ChaincodeEvent chaincodeEvent;

            ChaincodeEventCapture(String handle, BlockEvent blockEvent, ChaincodeEvent chaincodeEvent) {
                this.handle = handle;
                this.blockEvent = blockEvent;
                this.chaincodeEvent = chaincodeEvent;
            }
        }
        
        List<String> transactionList = new ArrayList<>();
        
        Vector<ChaincodeEventCapture> chaincodeEvents = new Vector<>(); // Test list to capture chaincode events.

        try {

            final String channelName = channel.getName();
            boolean isFooChain = FOO_CHANNEL_NAME.equals(channelName);
            out("Running channel %s", channelName);
            channel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
            channel.setDeployWaitTime(testConfig.getDeployWaitTime());

            Collection<Orderer> orderers = channel.getOrderers();
            final ChaincodeID chaincodeID;
            Collection<ProposalResponse> responses;
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            // Register a chaincode event listener that will trigger for any chaincode id and only for EXPECTED_EVENT_NAME event.

            String chaincodeEventListenerHandle = channel.registerChaincodeEventListener(Pattern.compile(".*"),
                    Pattern.compile(Pattern.quote(EXPECTED_EVENT_NAME)),
                    (handle, blockEvent, chaincodeEvent) -> {

                        chaincodeEvents.add(new ChaincodeEventCapture(handle, blockEvent, chaincodeEvent));

                        out("RECEIVEDChaincode event with handle: %s, chhaincode Id: %s, chaincode event name: %s, "
                                        + "transaction id: %s, event payload: \"%s\", from eventhub: %s",
                                handle, chaincodeEvent.getChaincodeId(),
                                chaincodeEvent.getEventName(), chaincodeEvent.getTxId(),
                                new String(chaincodeEvent.getPayload()), blockEvent.getEventHub().toString());

                    });

            //For non foo channel unregister event listener to test events are not called.
            if (!isFooChain) {
                channel.unregisterChaincodeEventListener(chaincodeEventListenerHandle);
                chaincodeEventListenerHandle = null;

            }

            chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build();

            if (installChaincode) {
                ////////////////////////////
                // Install Proposal Request
                //

                client.setUserContext(sampleOrg.getPeerAdmin());

                out("Creating install proposal");

                InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
                installProposalRequest.setChaincodeID(chaincodeID);
                out("isFooChain:%s", isFooChain);

                if (isFooChain) {
                    // on foo chain install from directory.

                    out("XXXXXXXX install proposal 1st");

                    ////For GO language and serving just a single user, chaincodeSource is mostly likely the users GOPATH
                    installProposalRequest.setChaincodeSourceLocation(new File(TEST_FIXTURES_PATH + "/sdkintegration/gocc/sample1"));
                } else {
                    // On bar chain install from an input stream.
                    out("XXXXXXXX install proposal 2nd");
                    installProposalRequest.setChaincodeInputStream(Util.generateTarGzInputStream(
                            (Paths.get(TEST_FIXTURES_PATH, "/sdkintegration/gocc/sample1", "src", CHAIN_CODE_PATH).toFile()),
                            Paths.get("src", CHAIN_CODE_PATH).toString()));

                }

                installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);

                out("Sending install proposal-------------------%s", isFooChain);


                ////////////////////////////
                // only a client from the same org as the peer can issue an install request
                int numInstallProposal = 0;
                //    Set<String> orgs = orgPeers.keySet();
                //   for (SampleOrg org : testSampleOrgs) {

                Set<Peer> peersFromOrg = sampleOrg.getPeers();
                numInstallProposal = numInstallProposal + peersFromOrg.size();
                responses = client.sendInstallProposal(installProposalRequest, peersFromOrg);

                for (ProposalResponse response : responses) {
                    if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                        out("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                        successful.add(response);
                        transactionList.add(response.getTransactionID());
                    } else {
                        failed.add(response);
                        transactionList.add(response.getTransactionID());
                    }
                }

                //   }
                out("Received %d install proposal responses. Successful+verified: %d . Failed: %d", numInstallProposal, successful.size(), failed.size());

                if (failed.size() > 0) {
                    ProposalResponse first = failed.iterator().next();
                    out("Not enough endorsers for install : %s" + successful.size() + ". %s " + first.getMessage());
                }
            }

            //   client.setUserContext(sampleOrg.getUser(TEST_ADMIN_NAME));
            //  final ChaincodeID chaincodeID = firstInstallProposalResponse.getChaincodeID();
            // Note installing chaincode does not require transaction no need to
            // send to Orderers

            ///////////////
            //// Instantiate chaincode.
            InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
            instantiateProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            instantiateProposalRequest.setChaincodeID(chaincodeID);
            instantiateProposalRequest.setFcn("init");
            instantiateProposalRequest.setArgs(new String[] {"a", data, "b", data});
            Map<String, byte[]> tm = new HashMap<>();
            tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
            tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
            instantiateProposalRequest.setTransientMap(tm);

            /*
              policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from someone in either Org1 or Org2
              See README.md Chaincode endorsement policies section for more details.
            */
            ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
            chaincodeEndorsementPolicy.fromYamlFile(new File(TEST_FIXTURES_PATH + "/sdkintegration/chaincodeendorsementpolicy.yaml"));
            instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

            out("Sending instantiateProposalRequest to all peers with arguments: a and b set to 100 and %s respectively", data, data);
            successful.clear();
            failed.clear();

            if (isFooChain) {  //Send responses both ways with specifying peers and by using those on the channel.
                responses = channel.sendInstantiationProposal(instantiateProposalRequest, channel.getPeers());
            } else {
                responses = channel.sendInstantiationProposal(instantiateProposalRequest);

            }
            for (ProposalResponse response : responses) {
                if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    out("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                    transactionList.add(response.getTransactionID());
                } else {
                    failed.add(response);
                    transactionList.add(response.getTransactionID());
                }
            }
            out("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size());
            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                out("Not enough endorsers for instantiate : %d" + successful.size() + "endorser failed with : %s" + first.getMessage() + ". Was verified : %s" + first.isVerified());
            }

            ///////////////
            /// Send instantiate transaction to orderer
            out("Sending instantiateTransaction to orderer with a and b set to %s and %s respectively", data, data);


            out("99999999999999999999999999");
            
            //queryChaincodeForExpectedValue(client, channel, "" ,chaincodeID);
            
            move(client, channel, installChaincode, sampleOrg, 0, orderers, successful, failed, chaincodeID, data);
        
            queryChaincodeForExpectedValue(client, channel, "" ,chaincodeID);
            	
            	
            out("77777777777777777777777777");
            
            // Channel queries

            // We can only send channel queries to peers that are in the same org as the SDK user context
            // Get the peers from the current org being used and pick one randomly to send the queries to.
            Set<Peer> peerSet = sampleOrg.getPeers();
            //  Peer queryPeer = peerSet.iterator().next();
            //   out("Using peer %s for channel queries", queryPeer.getName());

//            BlockchainInfo channelInfo = channel.queryBlockchainInfo();
//            out("Channel info for : " + channelName);
//            out("Channel height: " + channelInfo.getHeight());
//            String chainCurrentHash = Hex.encodeHexString(channelInfo.getCurrentBlockHash());
//            String chainPreviousHash = Hex.encodeHexString(channelInfo.getPreviousBlockHash());
//            out("Chain current block hash: " + chainCurrentHash);
//            out("Chainl previous block hash: " + chainPreviousHash);
//
//            // Query by block number. Should return latest block, i.e. block number 2
//            BlockInfo returnedBlock = channel.queryBlockByNumber(channelInfo.getHeight() - 1);
//            String previousHash = Hex.encodeHexString(returnedBlock.getPreviousHash());
//            out("queryBlockByNumber returned correct block with blockNumber " + returnedBlock.getBlockNumber()
//                    + " \n previous_hash " + previousHash);
//          
//            
//            out("channelInfo height : %s , returnedBlock : %s, ",channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());
//            out("chainPreviousHash : %s , previousHash %s : ", chainPreviousHash, previousHash);
//
//            // Query by block hash. Using latest block's previous hash so should return block number 1
//            byte[] hashQuery = returnedBlock.getPreviousHash();
//            returnedBlock = channel.queryBlockByHash(hashQuery);
//            out("queryBlockByHash returned block with blockNumber " + returnedBlock.getBlockNumber());
//            out("channelInfo's Height : %s, returnedBlock block number : %s", channelInfo.getHeight() - 2, returnedBlock.getBlockNumber());
//
//            // Query block by TxID. Since it's the last TxID, should be block 2
//            returnedBlock = channel.queryBlockByTransactionID(testTxID);
//            out("queryBlockByTxID returned block with blockNumber " + returnedBlock.getBlockNumber());
//            out("channelInfo's Height : %s, returnedBlock block number : %s", channelInfo.getHeight() - 1, returnedBlock.getBlockNumber());
//
//            // query transaction by ID
//            TransactionInfo txInfo = channel.queryTransactionByID(testTxID);
//            out("QueryTransactionByID returned TransactionInfo: txID " + txInfo.getTransactionID()
//                    + "\n     validation code " + txInfo.getValidationCode().getNumber());

            if (chaincodeEventListenerHandle != null) {

                channel.unregisterChaincodeEventListener(chaincodeEventListenerHandle);
                //Should be two. One event in chaincode and two notification for each of the two event hubs

                final int numberEventHubs = channel.getEventHubs().size();
                //just make sure we get the notifications.
                for (int i = 15; i > 0; --i) {
                    if (chaincodeEvents.size() == numberEventHubs) {
                        break;
                    } else {
                        Thread.sleep(90); // wait for the events.
                    }

                }
                
                out("numberEventHubs : %s, chaincodeEvents.size() : %s", numberEventHubs, chaincodeEvents.size());

                for (ChaincodeEventCapture chaincodeEventCapture : chaincodeEvents) {
                    out("chaincodeEventListenerHandle : %s, chaincodeEventCapture.handle : %s ", chaincodeEventListenerHandle, chaincodeEventCapture.handle);
                    
                    out("testTxID : %s, chaincodeEventCapture.chaincodeEvent.getTxId() : %s ", testTxID, chaincodeEventCapture.chaincodeEvent.getTxId());
                    
                    out("EXPECTED_EVENT_NAME : %s , chaincodeEventCapture.chaincodeEvent.getEventName() : %s ", EXPECTED_EVENT_NAME, chaincodeEventCapture.chaincodeEvent.getEventName());
                    
                    System.out.println(Arrays.equals(EXPECTED_EVENT_DATA, chaincodeEventCapture.chaincodeEvent.getPayload()));
                    
                    out("CHAIN_CODE_NAME : %s , chaincodeEventCapture.chaincodeEvent.getChaincodeId() : %s", CHAIN_CODE_NAME, chaincodeEventCapture.chaincodeEvent.getChaincodeId());

                    BlockEvent blockEvent = chaincodeEventCapture.blockEvent;
                    
                    out("channelName : %s , blockEvent.getChannelId() : %s " , channelName, blockEvent.getChannelId());
                    
                    System.out.println(channel.getEventHubs().contains(blockEvent.getEventHub()));

                }

            } else {
                out("chaincodeEvents.isEmpty() : %s", chaincodeEvents.isEmpty());
            }

            out("Running for Channel %s done", channelName);

        } catch (Exception e) {
            out("Caught an exception running channel %s", channel.getName());
            e.printStackTrace();
            //fail("Test failed with error : " + e.getMessage());
        }
        
        return transactionList;
    }
    
    
    

    
    private void move(HFClient client, Channel channel, boolean installChaincode, SampleOrg sampleOrg, int delta ,
    		Collection<Orderer> orderers, Collection<ProposalResponse> successful, Collection<ProposalResponse> failed, 
    		final ChaincodeID chaincodeID, String data) throws Exception {
    	
    	

    	out("XYXYXYXYXYXYXYXYXYXYXY");
    	
    	String data1 = "{\"agreementNo\": \"6500002197\", \"agreementType\": \"PO\", \"docType\": \"Purchase Order\" }";
    	
    	channel.sendTransaction(successful, orderers).thenApply(transactionEvent -> {

            waitOnFabric(0);

         //   assertTrue(transactionEvent.isValid()); // must be valid to be here.
            out("--- Finishedinstantiate transaction with transaction id %s, chainCodeId : %s", transactionEvent.getTransactionID(), chaincodeID);

            try {
                successful.clear();
                failed.clear();

                client.setUserContext(sampleOrg.getUser(TESTUSER_1_NAME));

                ///////////////
                /// Send transaction proposal to all peers
                TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
                transactionProposalRequest.setChaincodeID(chaincodeID);
                transactionProposalRequest.setFcn("invoke");
                transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
                transactionProposalRequest.setArgs(new String[] {"move", "a", "b", data1});

                Map<String, byte[]> tm2 = new HashMap<>();
                tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8)); //Just some extra junk in transient map
                tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8)); // ditto
                tm2.put("result", ":)".getBytes(UTF_8));  // This should be returned see chaincode why.
                tm2.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA);  //This should trigger an event see chaincode why.

                transactionProposalRequest.setTransientMap(tm2);

                out("sending transactionProposal to all peers with arguments: move(a,b,100)");

                Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
                
                out("ProposalResponse collection size : %s",transactionPropResp.size());
                
                
                for (ProposalResponse response : transactionPropResp) {
                    if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                        out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                        successful.add(response);
                    } else {
                        failed.add(response);
                    }
                }

                // Check that all the proposals are consistent with each other. We should have only one set
                // where all the proposals above are consistent. Note the when sending to Orderer this is done automatically.
                //  Shown here as an example that applications can invoke and select.
                // See org.hyperledger.fabric.sdk.proposal.consistency_validation config property.
                Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
                if (proposalConsistencySets.size() != 1) {
                    out(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
                }

                out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                        transactionPropResp.size(), successful.size(), failed.size());
                if (failed.size() > 0) {
                    ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                    out("Not enough endorsers for invoke(move a,b,100) : %s" + failed.size() + " endorser error : %s " +
                            firstTransactionProposalResponse.getMessage() +
                            ". Was verified : %s " + firstTransactionProposalResponse.isVerified());
                }
                out("Successfully received transaction proposal responses.");

                ProposalResponse resp = transactionPropResp.iterator().next();
                byte[] x = resp.getChaincodeActionResponsePayload(); // This is the data returned by the chaincode.
                String resultAsString = null;
                if (x != null) {
                    resultAsString = new String(x, "UTF-8");
                }
               // assertEquals(":)", resultAsString);
                
                out("resp.getChaincodeActionResponseStatus() : %s", resp.getChaincodeActionResponseStatus());
                
                //assertEquals(200, resp.getChaincodeActionResponseStatus()); //Chaincode's status.

                TxReadWriteSetInfo readWriteSetInfo = resp.getChaincodeActionResponseReadWriteSetInfo();
                //See blockwalker below how to transverse this
                
                //assertNotNull(readWriteSetInfo);
                //assertTrue(readWriteSetInfo.getNsRwsetCount() > 0);

                ChaincodeID cid = resp.getChaincodeID();
//                assertNotNull(cid);
//                assertEquals(CHAIN_CODE_PATH, cid.getPath());
//                assertEquals(CHAIN_CODE_NAME, cid.getName());
//                assertEquals(CHAIN_CODE_VERSION, cid.getVersion());
                
                out("CHAIN_CODE_PATH : %s, CHAIN_CODE_NAME : %s, CHAIN_CODE_VERSION : %s ", cid.getPath(), cid.getName(), cid.getVersion());
                
                ////////////////////////////
                // Send Transaction Transaction to orderer
                out("Sending chaincode transaction(move a,b,100) to orderer.");
                return channel.sendTransaction(successful).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);

            } catch (Exception e) {
                out("Caught an exception while invoking chaincode");
                e.printStackTrace();
                out("Failed invoking chaincode with error : %s " + e.getMessage());
            }

            return null;

        }).thenApply(transactionEvent -> {
            try {

                waitOnFabric(0);

            //    assertTrue(transactionEvent.isValid()); // must be valid to be here.
              
                out("Finished transaction with transaction id %s", transactionEvent.getTransactionID());
                
                testTxID = transactionEvent.getTransactionID(); // used in the channel queries later

                ////////////////////////////
                // Send Query Proposal to all peers
                //
                String expect = "" + (300 + delta);
                out("Now query chaincode for the value of b.");
                QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
                queryByChaincodeRequest.setArgs(new String[] {"query", "a"});
                queryByChaincodeRequest.setFcn("invoke");
                queryByChaincodeRequest.setChaincodeID(chaincodeID);

                Map<String, byte[]> tm2 = new HashMap<>();
                tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
                tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
                queryByChaincodeRequest.setTransientMap(tm2);

                Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
                for (ProposalResponse proposalResponse : queryProposals) {
                    if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                        out("Failed query proposal from peer : %s" + proposalResponse.getPeer().getName() + " status : %s " + proposalResponse.getStatus() +
                                ". Messages : %s " + proposalResponse.getMessage()
                                + ". Was verified : %s " + proposalResponse.isVerified());
                    } else {
                        String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                        out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
                        out("Expected output was : %s ", expect);
                       // assertEquals(payload, expect);
                    }
                }

                return null;
            } catch (Exception e) {
                out("Caught exception while running query");
                e.printStackTrace();
                //fail("Failed during chaincode query with error : " + e.getMessage());
            }

            return null;
        }).exceptionally(e -> {
            if (e instanceof TransactionEventException) {
                BlockEvent.TransactionEvent te = ((TransactionEventException) e).getTransactionEvent();
                if (te != null) {
                    out(format("Transaction with txid %s failed. %s", te.getTransactionID(), e.getMessage()));
                }
            }
            out(format("Test failed with %s exception %s", e.getClass().getName(), e.getMessage()));

            return null;
        }).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);

    }
    
    
    
    
    
    
    
    
    

    private void queryChaincodeForExpectedValue(HFClient client, Channel channel, final String expect, ChaincodeID chaincodeID) {

        out("Nowquerychaincode on channel %s for the value of b expecting to see: %s", channel.getName(), expect);
        QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
        queryByChaincodeRequest.setArgs(new String[] {"query", "b"});
        queryByChaincodeRequest.setFcn("invoke");
        queryByChaincodeRequest.setChaincodeID(chaincodeID);
        Collection<ProposalResponse> queryProposals;

        try {
            queryProposals = channel.queryByChaincode(queryByChaincodeRequest);
        } catch (Exception e) {
            throw new CompletionException(e);
        }

        for (ProposalResponse proposalResponse : queryProposals) {
            if (!proposalResponse.isVerified() || proposalResponse.getStatus() != Status.SUCCESS) {
                out("Failed query proposal from peer : %s " + proposalResponse.getPeer().getName() + " status : %s " + proposalResponse.getStatus() +
                        ". Messages : %s " + proposalResponse.getMessage()
                        + ". Was verified : %s" + proposalResponse.isVerified());
            } else {
                String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                out("Query payload of b from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
            }
        }
    }
    
    
    //CHECKSTYLE.ON: Method length is 320 lines (max allowed is 150).
    
    

    private Channel constructChannel(String name, HFClient client, SampleOrg sampleOrg) throws Exception {
        ////////////////////////////
        //Construct the channel
        //

        out("Constructing channel %s", name);

        //Only peer Admin org
        client.setUserContext(sampleOrg.getPeerAdmin());

        Collection<Orderer> orderers = new LinkedList<>();

        for (String orderName : sampleOrg.getOrdererNames()) {
        	
        	out("orderName : %s", orderName);

            Properties ordererProperties = testConfig.getOrdererProperties(orderName);

            //example of setting keepAlive to avoid timeouts on inactive http2 connections.
            // Under 5 minutes would require changes to server side to accept faster ping rates.
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {5L, TimeUnit.MINUTES});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] {8L, TimeUnit.SECONDS});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveWithoutCalls", new Object[] {true});

            orderers.add(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName),
                    ordererProperties));
        }

        //Just pick the first orderer in the list to create the channel.

        Orderer anOrderer = orderers.iterator().next();
        orderers.remove(anOrderer);

        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(TEST_FIXTURES_PATH + "/sdkintegration/e2e/channel/" + name + ".tx"));

        //Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.
        Channel newChannel = client.newChannel(name, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, sampleOrg.getPeerAdmin()));

        out("Created channel %s", name);

        for (String peerName : sampleOrg.getPeerNames()) {
            String peerLocation = sampleOrg.getPeerLocation(peerName);

            Properties peerProperties = testConfig.getPeerProperties(peerName); //test properties for peer.. if any.
            if (peerProperties == null) {
                peerProperties = new Properties();
            }
            //Example of setting specific options on grpc's NettyChannelBuilder
            peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);

            Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
            newChannel.joinPeer(peer);
            out("Peer %s joined channel %s", peerName, name);
            sampleOrg.addPeer(peer);
        }

        for (Orderer orderer : orderers) { //add remaining orderers if any.
            newChannel.addOrderer(orderer);
        }

        for (String eventHubName : sampleOrg.getEventHubNames()) {

            final Properties eventHubProperties = testConfig.getEventHubProperties(eventHubName);

            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[] {5L, TimeUnit.MINUTES});
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[] {8L, TimeUnit.SECONDS});

            EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                    eventHubProperties);
            newChannel.addEventHub(eventHub);
        }

        newChannel.initialize();

        out("Finished initialization channel %s", name);

        return newChannel;

    }

    static void out(String format, Object... args) {

        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();

    }

    private void waitOnFabric(int additional) {
        //NOOP today

    }

    private static final Map<String, String> TX_EXPECTED;

    static {
        TX_EXPECTED = new HashMap<>();
        TX_EXPECTED.put("readset1", "Missing readset for channel bar block 1");
        TX_EXPECTED.put("writeset1", "Missing writeset for channel bar block 1");
    }

    void blockWalker(HFClient client, Channel channel) throws InvalidArgumentException, ProposalException, IOException {
        try {
            BlockchainInfo channelInfo = channel.queryBlockchainInfo();

            for (long current = channelInfo.getHeight() - 1; current > -1; --current) {
                BlockInfo returnedBlock = channel.queryBlockByNumber(current);
                final long blockNumber = returnedBlock.getBlockNumber();

                out("current block number %d has data hash: %s", blockNumber, Hex.encodeHexString(returnedBlock.getDataHash()));
                out("current block number %d has previous hash id: %s", blockNumber, Hex.encodeHexString(returnedBlock.getPreviousHash()));
                out("current block number %d has calculated block hash is %s", blockNumber, Hex.encodeHexString(SDKUtils.calculateBlockHash(client,
                        blockNumber, returnedBlock.getPreviousHash(), returnedBlock.getDataHash())));

                final int envelopeCount = returnedBlock.getEnvelopeCount();
                out("1 : %s , envelopeCount : %s ", 1, envelopeCount);
                out("current block number %d has %d envelope count:", blockNumber, returnedBlock.getEnvelopeCount());
                int i = 0;
                for (BlockInfo.EnvelopeInfo envelopeInfo : returnedBlock.getEnvelopeInfos()) {
                    ++i;

                    out("  Transaction number %d has transaction id: %s", i, envelopeInfo.getTransactionID());
                    final String channelId = envelopeInfo.getChannelId();
                    //assertTrue("foo".equals(channelId) || "bar".equals(channelId));

                    out("  Transaction number %d has channel id: %s", i, channelId);
                    out("  Transaction number %d has epoch: %d", i, envelopeInfo.getEpoch());
                    out("  Transaction number %d has transaction timestamp: %tB %<te,  %<tY  %<tT %<Tp", i, envelopeInfo.getTimestamp());
                    out("  Transaction number %d has type id: %s", i, "" + envelopeInfo.getType());

                    if (envelopeInfo.getType() == TRANSACTION_ENVELOPE) {
                        BlockInfo.TransactionEnvelopeInfo transactionEnvelopeInfo = (BlockInfo.TransactionEnvelopeInfo) envelopeInfo;

                        out("  Transaction number %d has %d actions", i, transactionEnvelopeInfo.getTransactionActionInfoCount());
                      
                        //assertEquals(1, transactionEnvelopeInfo.getTransactionActionInfoCount()); // for now there is only 1 action per transaction.
                        
                        out("  Transaction number %d isValid %b", i, transactionEnvelopeInfo.isValid());
                        
                        //assertEquals(transactionEnvelopeInfo.isValid(), true);
                        
                        out("  Transaction number %d validation code %d", i, transactionEnvelopeInfo.getValidationCode());
                        
                        //assertEquals(0, transactionEnvelopeInfo.getValidationCode());

                        int j = 0;
                        for (BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo transactionActionInfo : transactionEnvelopeInfo.getTransactionActionInfos()) {
                            ++j;
                            out("   Transaction action %d has response status %d", j, transactionActionInfo.getResponseStatus());
                          //  assertEquals(200, transactionActionInfo.getResponseStatus());
                            out("   Transaction action %d has response message bytes as string: %s", j,
                                    printableString(new String(transactionActionInfo.getResponseMessageBytes(), "UTF-8")));
                            out("   Transaction action %d has %d endorsements", j, transactionActionInfo.getEndorsementsCount());
                            //assertEquals(2, transactionActionInfo.getEndorsementsCount());

                            for (int n = 0; n < transactionActionInfo.getEndorsementsCount(); ++n) {
                                BlockInfo.EndorserInfo endorserInfo = transactionActionInfo.getEndorsementInfo(n);
                                out("Endorser %d signature: %s", n, Hex.encodeHexString(endorserInfo.getSignature()));
                                out("Endorser %d endorser: %s", n, new String(endorserInfo.getEndorser(), "UTF-8"));
                            }
                            out("   Transaction action %d has %d chaincode input arguments", j, transactionActionInfo.getChaincodeInputArgsCount());
                            for (int z = 0; z < transactionActionInfo.getChaincodeInputArgsCount(); ++z) {
                                out("     Transaction action %d has chaincode input argument %d is: %s", j, z,
                                        printableString(new String(transactionActionInfo.getChaincodeInputArgs(z), "UTF-8")));
                            }

                            out("   Transaction action %d proposal response status: %d", j,
                                    transactionActionInfo.getProposalResponseStatus());
                            out("   Transaction action %d proposal response payload: %s", j,
                                    printableString(new String(transactionActionInfo.getProposalResponsePayload())));

                            // Check to see if we have our expected event.
                            if (blockNumber == 2) {
                            	out("checking if we have our expected event");
                                ChaincodeEvent chaincodeEvent = transactionActionInfo.getEvent();
                                out("chaincodeEvent : %s ", chaincodeEvent);
                                out("chaincodeEvent Payload : %s", chaincodeEvent.getPayload());

                                //assertTrue(Arrays.equals(EXPECTED_EVENT_DATA, chaincodeEvent.getPayload()));
                                
                                out("testTxID : %s , chaincodeEvent.getTxId() : %s", testTxID, chaincodeEvent.getTxId());
                                out("CHAIN_CODE_NAME : %s , chaincodeEvent.getChaincodeId() : %s",CHAIN_CODE_NAME, chaincodeEvent.getChaincodeId());
                                out("EXPECTED_EVENT_NAME : %s , chaincodeEvent.getEventName() : %s",EXPECTED_EVENT_NAME, chaincodeEvent.getEventName());

                            }

                            TxReadWriteSetInfo rwsetInfo = transactionActionInfo.getTxReadWriteSet();
                            if (null != rwsetInfo) {
                                out("   Transaction action %d has %d name space read write sets", j, rwsetInfo.getNsRwsetCount());

                                for (TxReadWriteSetInfo.NsRwsetInfo nsRwsetInfo : rwsetInfo.getNsRwsetInfos()) {
                                    final String namespace = nsRwsetInfo.getNamespace();
                                    KvRwset.KVRWSet rws = nsRwsetInfo.getRwset();

                                    int rs = -1;
                                    for (KvRwset.KVRead readList : rws.getReadsList()) {
                                        rs++;

                                        out("     Namespace %s read set %d key %s  version [%d:%d]", namespace, rs, readList.getKey(),
                                                readList.getVersion().getBlockNum(), readList.getVersion().getTxNum());

                                        if ("bar".equals(channelId) && blockNumber == 2) {
                                            if ("example_cc_go".equals(namespace)) {
                                                if (rs == 0) {
                                                    out("a : %s , readList.getKey() : %s ","a", readList.getKey());
                                                    out("1 : %s, readList.getVersion().getBlockNum() : %s", 1, readList.getVersion().getBlockNum());
                                                    out("0 : %s , readList.getVersion().getTxNum() : %s ", 0, readList.getVersion().getTxNum());
                                                } else if (rs == 1) {
                                                	out("b : %s , readList.getKey() : %s ", "b", readList.getKey());
                                                	out("1 : %s , readList.getVersion().getBlockNum() : %s ", 1, readList.getVersion().getBlockNum());
                                                	out("0 : %s, readList.getVersion().getTxNum() : %s ", 0, readList.getVersion().getTxNum());
                                                } else {
                                                    out(format("unexpected readset %d", rs));
                                                }

                                                TX_EXPECTED.remove("readset1");
                                            }
                                        }
                                    }

                                    rs = -1;
                                    for (KvRwset.KVWrite writeList : rws.getWritesList()) {
                                        rs++;
                                        String valAsString = printableString(new String(writeList.getValue().toByteArray(), "UTF-8"));

                                        out("     Namespace %s write set %d key %s has value '%s' ", namespace, rs,
                                                writeList.getKey(),
                                                valAsString);

                                        if ("bar".equals(channelId) && blockNumber == 2) {
                                            if (rs == 0) {
                                                out("a : %s , writeList.getKey() : %s " , "a", writeList.getKey());
                                                out("400 : %s , valAsString : %s", "400", valAsString);
                                            } else if (rs == 1) {
                                            	out("b : %s, writeList.getKey() : %s ", "b", writeList.getKey());
                                            	out("400 : %s, valAsString : %s", "400", valAsString);
                                            } else {
                                                out(format("unexpected writeset %d", rs));
                                            }

                                            TX_EXPECTED.remove("writeset1");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!TX_EXPECTED.isEmpty()) {
                out(TX_EXPECTED.get(0));
            }
        } catch (InvalidProtocolBufferRuntimeException e) {
            throw e.getCause();
        }
    }

    static String printableString(final String string) {
        int maxLogStringLength = 64;
        if (string == null || string.length() == 0) {
            return string;
        }

        String ret = string.replaceAll("[^\\p{Print}]", "?");

        ret = ret.substring(0, Math.min(ret.length(), maxLogStringLength)) + (ret.length() > maxLogStringLength ? "..." : "");

        return ret;

    }

}
