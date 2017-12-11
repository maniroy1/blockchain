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
import org.hyperledger.fabric.protos.common.Configtx;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset;
import org.hyperledger.fabric.protos.peer.Query.ChaincodeInfo;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockInfo;
import org.hyperledger.fabric.sdk.BlockchainInfo;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.ChaincodeID;
import org.hyperledger.fabric.sdk.ChaincodeResponse.Status;
import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.ChannelConfiguration;
import org.hyperledger.fabric.sdk.EventHub;
import org.hyperledger.fabric.sdk.HFClient;
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
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.HFCAInfo;

import com.moglix.reports.fabric.sdk.TestConfigHelper;
import com.moglix.reports.fabric.sdk.testutils.TestConfig;

public class Operations {
	

    private static final TestConfig testConfig = TestConfig.getConfig();
    private static final String TEST_ADMIN_NAME = "admin";
    private static final String TESTUSER_1_NAME = "user1";
    private static final String TEST_FIXTURES_PATH = "src/main/java/com/moglix/reports/fixture";

    private static final String CHAIN_CODE_NAME = "example_cc_go";
    private static final String CHAIN_CODE_PATH = "github.com/example_cc";
    private static final String CHAIN_CODE_VERSION = "32";

    private static final String FOO_CHANNEL_NAME = "foo";
    private static final String BAR_CHANNEL_NAME = "bar";

    private static final byte[] EXPECTED_EVENT_DATA = "!".getBytes(UTF_8);
    private static final String EXPECTED_EVENT_NAME = "event";

    String testTxID = null;  // save the CC invoke TxID and use in queries

    private final TestConfigHelper configHelper = new TestConfigHelper();

    private Collection<SampleOrg> testSampleOrgs;
    
    
    
    public static void main(String args[]) {
    	Operations e = new Operations();
    	try {
			e.checkConfig();
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException
				| MalformedURLException | org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	e.setup(1, "testing");
    }
    
    public List<String> tranaction(int delta, String data) {
    	
    	try {
			checkConfig();
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException
				| MalformedURLException | org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    	return setup(delta, data);
    	
    }
    
    
    public void checkConfig() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, MalformedURLException, org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException {
        out("\n\n\nRUNNING: Operations.\n");
        //   configHelper.clearConfig();
        //   assertEquals(256, Config.getConfig().getSecurityLevel());
        resetConfig();
        configHelper.customizeConfig();

        testSampleOrgs = testConfig.getIntegrationTestsSampleOrgs();
        //Set up hfca for each sample org

        for (SampleOrg sampleOrg : testSampleOrgs) {
            String caName = sampleOrg.getCAName(); //Try one of each name and no name.
            if (caName != null && !caName.isEmpty()) {
                sampleOrg.setCAClient(HFCAClient.createNewInstance(caName, sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            } else {
                sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            }
        }
    }

    public List<String> setup(int delta, String data) {
    	
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
            File sampleStoreFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");
            if (sampleStoreFile.exists()) { //For testing start fresh
                sampleStoreFile.delete();
            }

            final SampleStore sampleStore = new SampleStore(sampleStoreFile);
            //  sampleStoreFile.deleteOnExit();

            //SampleUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface

            ////////////////////////////
            // get users for all orgs
            
            out("testSampleOrgs size : %s ", testSampleOrgs.size());

            for (SampleOrg sampleOrg : testSampleOrgs) {

                HFCAClient ca = sampleOrg.getCAClient();

                final String orgName = sampleOrg.getName();
                final String mspid = sampleOrg.getMSPID();
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
//                if (!user.isEnrolled()) {
//                    RegistrationRequest rr1 = new RegistrationRequest(user.getName(), "org1.department1");
//                    user.setEnrollmentSecret(ca.register(rr1, admin));
//                    user.setEnrollment(ca.enroll(user.getName(), user.getEnrollmentSecret()));
//                    user.setMspId(mspid);
//                }
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
            
			Channel fooChannel = reconstructChannel(FOO_CHANNEL_NAME, client, sampleOrg, sampleStore);
            
            res = runChannel(client, fooChannel, true, sampleOrg, delta, data);
            
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
			
//			Channel barChannel = constructChannel(BAR_CHANNEL_NAME, client, sampleOrg);
//            /**
//             * sampleStore.saveChannel uses {@link Channel#serializeChannel()}
//             */
//            sampleStore.saveChannel(barChannel);
//            runChannel(client, barChannel, true, sampleOrg, 100); //run a newly constructed bar channel with different b value!
//            //let bar channel just shutdown so we have both scenarios.

           // out("\nTraverse the blocks for chain %s ", barChannel.getName());
            out("That's all folks!");

        } catch (Exception e) {
            e.printStackTrace();

            //fail(e.getMessage());
        }
        
        return res;

    }

    //CHECKSTYLE.OFF: Method length is 320 lines (max allowed is 150).
    List<String> runChannel(HFClient client, Channel channel, boolean installChaincode, SampleOrg sampleOrg, int delta, String data) {

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
            client.setUserContext(sampleOrg.getPeerAdmin());


            switch(delta) {	
	            case 0 :
	            	return move(client, channel, installChaincode, sampleOrg, delta, orderers, successful, failed, chaincodeID, data);
	            case 1 : 
		            return queryChaincodeForExpectedValue(client, channel, "" ,chaincodeID);
	            case 2 :
		            return blockWalker(client, channel);
	            default :
	            	out("Not a valid option, Go have Fun!");
            }

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
                
            } else {
                out("chaincodeEvents.isEmpty() : %s", chaincodeEvents.isEmpty());
            }

            out("Running for Channel %s done", channelName);

        } catch (Exception e) {
            out("Caught an exception running channel %s", channel.getName());
            e.printStackTrace();
            //fail("Test failed with error : " + e.getMessage());
        }
		return null;
    }
    
    
    

    
    private List<String> move(HFClient client, Channel channel, boolean installChaincode, SampleOrg sampleOrg, int delta ,
    		Collection<Orderer> orderers, Collection<ProposalResponse> successful, Collection<ProposalResponse> failed, 
    		final ChaincodeID chaincodeID, String data) throws Exception {
    	
    	List<String> transactionId = new ArrayList<>();
    	
    		out("Inside Trnsaction Module");
            waitOnFabric(0);

            try {
                successful.clear();
                failed.clear();

                client.setUserContext(sampleOrg.getPeerAdmin());

                TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
                transactionProposalRequest.setChaincodeID(chaincodeID);
                transactionProposalRequest.setFcn("invoke");
                transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
                transactionProposalRequest.setArgs(new String[] {"move", "b", "a", data});

                Map<String, byte[]> tm2 = new HashMap<>();
                tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8)); //Just some extra junk in transient map
                tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8)); // ditto
                tm2.put("result", ":)".getBytes(UTF_8));  // This should be returned see chaincode why.
                tm2.put(EXPECTED_EVENT_NAME, EXPECTED_EVENT_DATA);  //This should trigger an event see chaincode why.

                transactionProposalRequest.setTransientMap(tm2);

                out("sending transactionProposal to all peers with arguments: move(b,a,100)");

                Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
                
                out("ProposalResponse collection size : %s",transactionPropResp.size());
                
                
                for (ProposalResponse response : transactionPropResp) {
                    if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                        out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                        testTxID = response.getTransactionID();
                        transactionId.add(testTxID);
                        successful.add(response);
                    } else {
                        failed.add(response);
                    }
                }

                Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
                if (proposalConsistencySets.size() != 1) {
                    out(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
                }

                out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                        transactionPropResp.size(), successful.size(), failed.size());
                if (failed.size() > 0) {
                    ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                    out("Not enough endorsers for invoke(move a,b,100) : %s" + failed.size() + " endorser error: %s" +
                            firstTransactionProposalResponse.getMessage() +
                            ". Was verified: %s" + firstTransactionProposalResponse.isVerified());
                }
                out("Successfully received transaction proposal responses.");

                ProposalResponse resp = transactionPropResp.iterator().next();
                byte[] x = resp.getChaincodeActionResponsePayload(); // This is the data returned by the chaincode.
                String resultAsString = null;
                if (x != null) {
                    resultAsString = new String(x, "UTF-8");
                }
                
                out("resp.getChaincodeActionResponseStatus() : %s", resp.getChaincodeActionResponseStatus());
                

                TxReadWriteSetInfo readWriteSetInfo = resp.getChaincodeActionResponseReadWriteSetInfo();

                ChaincodeID cid = resp.getChaincodeID();
                
                out("CHAIN_CODE_PATH : %s, CHAIN_CODE_NAME : %s, CHAIN_CODE_VERSION : %s ", cid.getPath(), cid.getName(), cid.getVersion());
                
                out("Sending chaincode transaction(move a,b,100) to orderer.");
                channel.sendTransaction(successful).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);

            } catch (Exception e) {
                out("Caught an exception while invoking chaincode");
                e.printStackTrace();
                out("Failed invoking chaincode with error : %s" + e.getMessage());
            }

            return transactionId;

    }
    
    private List<String> queryChaincodeForExpectedValue(HFClient client, Channel channel, final String expect, ChaincodeID chaincodeID) {
    	
        out("Nowquerychaincode on channel %s for the value of b expecting to see: %s", channel.getName(), expect);
        
        List<String> queryChaincodeRes = new ArrayList<>();

        QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
        queryByChaincodeRequest.setArgs(new String[] {"query", "a"});
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
                out("Failed query proposal from peer : %s" + proposalResponse.getPeer().getName() + " status : %s" + proposalResponse.getStatus() +
                        ". Messages : %s " + proposalResponse.getMessage()
                        + ". Was verified : %s" + proposalResponse.isVerified());
            } else {
                String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                out("Query payload of a from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
                queryChaincodeRes.add(payload);
            }
        }
        
        return queryChaincodeRes;
        
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

    List<String> blockWalker(HFClient client, Channel channel) throws InvalidArgumentException, ProposalException, IOException {
    	List<String> blockWalkerList = new ArrayList<>();
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
                                        out("--------------------> %s ", valAsString);
                                        if(!namespace.equals("lscc"))
                                        	blockWalkerList.add(valAsString);

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
                out("%s",TX_EXPECTED.get(0));
            }
        } catch (InvalidProtocolBufferRuntimeException e) {
            throw e.getCause();
        }
        return blockWalkerList;
    }

    static String printableString(final String string) {
        int maxLogStringLength = 2048;
        if (string == null || string.length() == 0) {
            return string;
        }

        String ret = string.replaceAll("[^\\p{Print}]", "?");

        ret = ret.substring(0, Math.min(ret.length(), maxLogStringLength)) + (ret.length() > maxLogStringLength ? "..." : "");

        return ret;

    }
    
    private Channel reconstructChannel(String name, HFClient client, SampleOrg sampleOrg, SampleStore sampleStore) throws Exception {
        out("Reconstructing %s channel", name);

        client.setUserContext(sampleOrg.getPeerAdmin());

        Channel newChannel;

        if (BAR_CHANNEL_NAME.equals(name)) { // bar channel was stored in samplestore in End2endIT testcase.

            /**
             *  sampleStore.getChannel uses {@link HFClient#deSerializeChannel(byte[])}
             */
            newChannel = sampleStore.getChannel(client, name);

            out("Retrieved channel %s from sample store.", name);

        } else {
            // foo channel do manual reconstruction.

            newChannel = client.newChannel(name);

            for (String ordererName : sampleOrg.getOrdererNames()) {
                newChannel.addOrderer(client.newOrderer(ordererName, sampleOrg.getOrdererLocation(ordererName),
                        testConfig.getOrdererProperties(ordererName)));
            }

            for (String peerName : sampleOrg.getPeerNames()) {
                String peerLocation = sampleOrg.getPeerLocation(peerName);
                Peer peer = client.newPeer(peerName, peerLocation, testConfig.getPeerProperties(peerName));
                newChannel.addPeer(peer);
                sampleOrg.addPeer(peer);
            }

            for (String eventHubName : sampleOrg.getEventHubNames()) {
                EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                        testConfig.getEventHubProperties(eventHubName));
                newChannel.addEventHub(eventHub);
            }
        }

        newChannel.initialize();

        //Query the actual peer for which channels it belongs to and check it belongs to this channel
        for (Peer peer : newChannel.getPeers()) {
            Set<String> channels = client.queryChannels(peer);
            if (!channels.contains(name)) {
                throw new AssertionError(format("Peer %s does not appear to belong to channel %s", peer.getName(), name));
            }
        }
        //Just see if we can get channelConfiguration. Not required for the rest of scenario but should work.
        final byte[] channelConfigurationBytes = newChannel.getChannelConfigurationBytes();
        Configtx.Config channelConfig = Configtx.Config.parseFrom(channelConfigurationBytes);
//        assertNotNull(channelConfig);
        Configtx.ConfigGroup channelGroup = channelConfig.getChannelGroup();
//        assertNotNull(channelGroup);
        Map<String, Configtx.ConfigGroup> groupsMap = channelGroup.getGroupsMap();
  //      assertNotNull(groupsMap.get("Orderer"));
    //    assertNotNull(groupsMap.get("Application"));

        //Before return lets see if we have the chaincode on the peers that we expect from End2endIT
        //And if they were instantiated too.

        for (Peer peer : newChannel.getPeers()) {

            if (!checkInstalledChaincode(client, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {
                throw new AssertionError(format("Peer %s is missing chaincode name: %s, path:%s, version: %s",
                        peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_PATH));
            }

            if (!checkInstantiatedChaincode(newChannel, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {

                throw new AssertionError(format("Peer %s is missing instantiated chaincode name: %s, path:%s, version: %s",
                        peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_PATH));
            }

        }

        out("Finished reconstructing channel %s.", name);

        return newChannel;
    }

    private static boolean checkInstalledChaincode(HFClient client, Peer peer, String ccName, String ccPath, String ccVersion) throws InvalidArgumentException, ProposalException {

        out("Checking installed chaincode: %s, at version: %s, on peer: %s", ccName, ccVersion, peer.getName());
        List<ChaincodeInfo> ccinfoList = client.queryInstalledChaincodes(peer);

        boolean found = false;

        for (ChaincodeInfo ccifo : ccinfoList) {

            found = ccName.equals(ccifo.getName()) && ccPath.equals(ccifo.getPath()) && ccVersion.equals(ccifo.getVersion());
            if (found) {
                break;
            }
        }
        return found;
    }
    
    private static boolean checkInstantiatedChaincode(Channel channel, Peer peer, String ccName, String ccPath, String ccVersion) throws InvalidArgumentException, ProposalException {
        out("Checking instantiated chaincode: %s, at version: %s, on peer: %s", ccName, ccVersion, peer.getName());
        List<ChaincodeInfo> ccinfoList = channel.queryInstantiatedChaincodes(peer);

        boolean found = false;

        for (ChaincodeInfo ccifo : ccinfoList) {
            found = ccName.equals(ccifo.getName()) && ccPath.equals(ccifo.getPath()) && ccVersion.equals(ccifo.getVersion());
            if (found) {
                break;
            }

        }

        return found;
    }
}
