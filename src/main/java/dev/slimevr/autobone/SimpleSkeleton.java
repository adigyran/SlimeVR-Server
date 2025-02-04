package dev.slimevr.autobone;

import java.util.HashMap;
import java.util.Map;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import dev.slimevr.poserecorder.TrackerFrame;
import dev.slimevr.poserecorder.TrackerFrameData;
import io.eiren.vr.processor.HumanSkeletonWithLegs;
import io.eiren.vr.processor.HumanSkeletonWithWaist;
import io.eiren.vr.processor.TransformNode;
import io.eiren.vr.trackers.TrackerPosition;
import io.eiren.vr.trackers.TrackerUtils;
import io.eiren.yaml.YamlFile;

public class SimpleSkeleton {
	
	// Upper body (torso)
	protected final TransformNode hmdNode = new TransformNode("HMD", false);
	protected final TransformNode headNode = new TransformNode("Head", false);
	protected final TransformNode neckNode = new TransformNode("Neck", false);
	protected final TransformNode waistNode = new TransformNode("Waist", false);
	protected final TransformNode chestNode = new TransformNode("Chest", false);
	protected final TransformNode hipNode = new TransformNode("Hip", false);
	
	/**
	 * Distance from shoulders to chest
	 */
	protected float chestDistance = 0.35f;
	/**
	 * Distance from hip to waist
	 */
	protected float waistDistance = 0.1f;
	/**
	 * Distance from shoulder to hip
	 */
	protected float torsoLength = 0.7f;
	/**
	 * Distance from eyes to the base of the neck
	 */
	protected float neckLength = HumanSkeletonWithWaist.NECK_LENGTH_DEFAULT;
	/**
	 * Distance from eyes to ear
	 */
	protected float headShift = HumanSkeletonWithWaist.HEAD_SHIFT_DEFAULT;
	
	// Lower body (legs)
	protected final TransformNode leftHipNode = new TransformNode("Left-Hip", false);
	protected final TransformNode leftKneeNode = new TransformNode("Left-Knee", false);
	protected final TransformNode leftAnkleNode = new TransformNode("Left-Ankle", false);
	protected final TransformNode rightHipNode = new TransformNode("Right-Hip", false);
	protected final TransformNode rightKneeNode = new TransformNode("Right-Knee", false);
	protected final TransformNode rightAnkleNode = new TransformNode("Right-Ankle", false);
	
	/**
	 * Distance between centers of both hips
	 */
	protected float hipsWidth = HumanSkeletonWithLegs.HIPS_WIDTH_DEFAULT;
	/**
	 * Length from hip to knees
	 */
	protected float kneeHeight = 0.42f;
	/**
	 * Distance from hip to ankle
	 */
	protected float legsLength = 0.84f;
	
	protected final HashMap<String, TransformNode> nodes = new HashMap<String, TransformNode>();
	
	private Quaternion rotBuf1 = new Quaternion();
	private Quaternion rotBuf2 = new Quaternion();
	
	public SimpleSkeleton() {
		// Assemble skeleton to hip
		hmdNode.attachChild(headNode);
		headNode.localTransform.setTranslation(0, 0, headShift);
		
		headNode.attachChild(neckNode);
		neckNode.localTransform.setTranslation(0, -neckLength, 0);
		
		neckNode.attachChild(chestNode);
		chestNode.localTransform.setTranslation(0, -chestDistance, 0);
		
		chestNode.attachChild(waistNode);
		waistNode.localTransform.setTranslation(0, (chestDistance - torsoLength + waistDistance), 0);

		waistNode.attachChild(hipNode);
		hipNode.localTransform.setTranslation(0, -waistDistance, 0);
		
		// Assemble skeleton to feet
		hipNode.attachChild(leftHipNode);
		leftHipNode.localTransform.setTranslation(-hipsWidth / 2, 0, 0);
		
		hipNode.attachChild(rightHipNode);
		rightHipNode.localTransform.setTranslation(hipsWidth / 2, 0, 0);
		
		leftHipNode.attachChild(leftKneeNode);
		leftKneeNode.localTransform.setTranslation(0, -(legsLength - kneeHeight), 0);
		
		rightHipNode.attachChild(rightKneeNode);
		rightKneeNode.localTransform.setTranslation(0, -(legsLength - kneeHeight), 0);
		
		leftKneeNode.attachChild(leftAnkleNode);
		leftAnkleNode.localTransform.setTranslation(0, -kneeHeight, 0);
		
		rightKneeNode.attachChild(rightAnkleNode);
		rightAnkleNode.localTransform.setTranslation(0, -kneeHeight, 0);
		
		// Set up a HashMap to get nodes by name easily
		hmdNode.depthFirstTraversal(visitor -> {
			nodes.put(visitor.getName(), visitor);
		});
	}
	
	public SimpleSkeleton(Map<String, Float> configs, Map<String, Float> altConfigs) {
		// Initialize
		this();
		
		// Set configs
		if(altConfigs != null) {
			// Set alts first, so if there's any overlap it doesn't affect the values
			setSkeletonConfigs(altConfigs);
		}
		setSkeletonConfigs(configs);
	}
	
	public SimpleSkeleton(Map<String, Float> configs) {
		this(configs, null);
	}
	
	public void setPoseFromFrame(TrackerFrame[] frame) {
		
		TrackerFrame hmd = TrackerUtils.findTrackerForBodyPosition(frame, TrackerPosition.HMD);
		
		if(hmd != null) {
			if(hmd.hasData(TrackerFrameData.ROTATION)) {
				hmdNode.localTransform.setRotation(hmd.rotation);
				headNode.localTransform.setRotation(hmd.rotation);
			}
			
			if(hmd.hasData(TrackerFrameData.POSITION)) {
				hmdNode.localTransform.setTranslation(hmd.position);
			}
		}
		TrackerFrame chest = TrackerUtils.findTrackerForBodyPosition(frame, TrackerPosition.CHEST, TrackerPosition.WAIST, TrackerPosition.HIP);
		setRotation(chest, neckNode);
		
		TrackerFrame waist = TrackerUtils.findTrackerForBodyPosition(frame, TrackerPosition.WAIST, TrackerPosition.CHEST, TrackerPosition.HIP);
		setRotation(waist, chestNode);
		
		TrackerFrame leftLeg = TrackerUtils.findTrackerForBodyPosition(frame, TrackerPosition.LEFT_LEG);
		TrackerFrame rightLeg = TrackerUtils.findTrackerForBodyPosition(frame, TrackerPosition.RIGHT_LEG);
		
		TrackerFrame hip = TrackerUtils.findTrackerForBodyPosition(frame, TrackerPosition.HIP, TrackerPosition.WAIST, TrackerPosition.CHEST);
		averagePelvis(hip, leftLeg, rightLeg);
		
		setRotation(leftLeg, leftHipNode);
		setRotation(rightLeg, rightHipNode);
		
		TrackerFrame leftAnkle = TrackerUtils.findTrackerForBodyPosition(frame, TrackerPosition.LEFT_ANKLE);
		setRotation(leftAnkle, rightKneeNode);
		
		TrackerFrame rightAnkle = TrackerUtils.findTrackerForBodyPosition(frame, TrackerPosition.RIGHT_ANKLE);
		setRotation(rightAnkle, leftKneeNode);
		
		updatePose();
	}
	
	public void setRotation(TrackerFrame trackerFrame, TransformNode node) {
		if(trackerFrame != null && trackerFrame.hasData(TrackerFrameData.ROTATION)) {
			node.localTransform.setRotation(trackerFrame.rotation);
		}
	}
	
	public void averagePelvis(TrackerFrame hip, TrackerFrame leftLeg, TrackerFrame rightLeg) {
		if((leftLeg == null || rightLeg == null) || (!leftLeg.hasData(TrackerFrameData.ROTATION) || !rightLeg.hasData(TrackerFrameData.ROTATION))) {
			setRotation(hip, waistNode);
			return;
		}
		if(hip == null || !hip.hasData(TrackerFrameData.ROTATION)) {
			if(leftLeg.hasData(TrackerFrameData.ROTATION) && rightLeg.hasData(TrackerFrameData.ROTATION)) {
				leftLeg.getRotation(rotBuf1);
				rightLeg.getRotation(rotBuf2);
				rotBuf1.nlerp(rotBuf2, 0.5f);
				
				hipNode.localTransform.setRotation(rotBuf1);
			}
			
			return;
		}
		
		// Average the pelvis with the waist rotation
		leftLeg.getRotation(rotBuf1);
		rightLeg.getRotation(rotBuf2);
		rotBuf1.nlerp(rotBuf2, 0.5f);
		hip.getRotation(rotBuf2);
		rotBuf1.nlerp(rotBuf2, 0.3333333f);
		
		hipNode.localTransform.setRotation(rotBuf1);
	}
	
	public void setSkeletonConfigs(Map<String, Float> configs) {
		configs.forEach(this::setSkeletonConfig);
	}
	
	public void setSkeletonConfig(String joint, float newLength) {
		setSkeletonConfig(joint, newLength, false);
	}
	
	public void setSkeletonConfig(String joint, float newLength, boolean updatePose) {
		switch(joint) {
		case "Head":
			headShift = newLength;
			headNode.localTransform.setTranslation(0, 0, headShift);
			if(updatePose) {
				headNode.update();
			}
			break;
		case "Neck":
			neckLength = newLength;
			neckNode.localTransform.setTranslation(0, -neckLength, 0);
			if(updatePose) {
				neckNode.update();
			}
			break;
		case "Torso":
			torsoLength = newLength;
			waistNode.localTransform.setTranslation(0, (chestDistance - torsoLength + waistDistance), 0);
			if(updatePose) {
				hipNode.update();
			}
			break;
		case "Chest":
			chestDistance = newLength;
			chestNode.localTransform.setTranslation(0, -chestDistance, 0);
			waistNode.localTransform.setTranslation(0, (chestDistance - torsoLength + waistDistance), 0);
			if(updatePose) {
				chestNode.update();
			}
			break;
		case "Waist":
			waistDistance = newLength;
			waistNode.localTransform.setTranslation(0, (chestDistance - torsoLength + waistDistance), 0);
			hipNode.localTransform.setTranslation(0, -waistDistance, 0);
			if(updatePose) {
				waistNode.update();
			}
			break;	
		case "Hips width":
			hipsWidth = newLength;
			leftHipNode.localTransform.setTranslation(-hipsWidth / 2, 0, 0);
			rightHipNode.localTransform.setTranslation(hipsWidth / 2, 0, 0);
			if(updatePose) {
				leftHipNode.update();
				rightHipNode.update();
			}
			break;
		case "Knee height":
			kneeHeight = newLength;
			leftAnkleNode.localTransform.setTranslation(0, -kneeHeight, 0);
			rightAnkleNode.localTransform.setTranslation(0, -kneeHeight, 0);
			leftKneeNode.localTransform.setTranslation(0, -(legsLength - kneeHeight), 0);
			rightKneeNode.localTransform.setTranslation(0, -(legsLength - kneeHeight), 0);
			if(updatePose) {
				leftKneeNode.update();
				rightKneeNode.update();
			}
			break;
		case "Legs length":
			legsLength = newLength;
			leftKneeNode.localTransform.setTranslation(0, -(legsLength - kneeHeight), 0);
			rightKneeNode.localTransform.setTranslation(0, -(legsLength - kneeHeight), 0);
			if(updatePose) {
				leftKneeNode.update();
				rightKneeNode.update();
			}
			break;
		}
	}
	
	public Float getSkeletonConfig(String joint) {
		switch(joint) {
		case "Head":
			return headShift;
		case "Neck":
			return neckLength;
		case "Torso":
			return torsoLength;	
		case "Waist":
			return waistDistance;
		case "Chest":
			return chestDistance;
		case "Hips width":
			return hipsWidth;
		case "Knee height":
			return kneeHeight;
		case "Legs length":
			return legsLength;
		}
		
		return null;
	}
	
	public void updatePose() {
		hmdNode.update();
	}
	
	public TransformNode getNode(String node) {
		return nodes.get(node);
	}
	
	public TransformNode getNode(TrackerPosition bodyPosition) {
		return getNode(bodyPosition, false);
	}
	
	public TransformNode getNode(TrackerPosition bodyPosition, boolean rotationNode) {
		if(bodyPosition == null) {
			return null;
		}
		
		switch(bodyPosition) {
		case HMD:
			return hmdNode;
		case CHEST:
			return rotationNode ? neckNode : chestNode;
		case WAIST:
			return rotationNode ? chestNode : waistNode;
		case HIP:
			return rotationNode ? waistNode : hipNode;
		
		case LEFT_LEG:
			return rotationNode ? leftHipNode : leftKneeNode;
		case RIGHT_LEG:
			return rotationNode ? rightHipNode : rightKneeNode;
		
		case LEFT_ANKLE:
			return rotationNode ? leftKneeNode : leftAnkleNode;
		case RIGHT_ANKLE:
			return rotationNode ? rightKneeNode : rightAnkleNode;
		}
		
		return null;
	}
	
	public Vector3f getNodePosition(String node) {
		TransformNode transformNode = getNode(node);
		return transformNode != null ? transformNode.worldTransform.getTranslation() : null;
	}
	
	public Vector3f getNodePosition(TrackerPosition bodyPosition) {
		TransformNode node = getNode(bodyPosition);
		if(node == null) {
			return null;
		}
		
		return node.worldTransform.getTranslation();
	}
	
	public void saveConfigs(YamlFile config) {
		// Save waist configs
		config.setProperty("body.headShift", headShift);
		config.setProperty("body.neckLength", neckLength);
		config.setProperty("body.waistDistance", waistDistance);
		config.setProperty("body.chestDistance", chestDistance);
		config.setProperty("body.torsoLength", torsoLength);
		
		// Save leg configs
		config.setProperty("body.hipsWidth", hipsWidth);
		config.setProperty("body.kneeHeight", kneeHeight);
		config.setProperty("body.legsLength", legsLength);
	}
}
