/*
 Copyright (c) 2013-2019, Stephen Gold
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in the
 documentation and/or other materials provided with the distribution.
 * Neither the name of the copyright holder nor the names of its contributors
 may be used to endorse or promote products derived from this software without
 specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package jme3utilities.minie;

import com.jme3.app.state.AppState;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSoftSpace;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RayTestFlag;
import com.jme3.bullet.SoftBodyWorldInfo;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.debug.BulletDebugAppState;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.bullet.joints.SixDofJoint;
import com.jme3.bullet.joints.motors.RotationalLimitMotor;
import com.jme3.bullet.joints.motors.TranslationalLimitMotor;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsCharacter;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.objects.PhysicsSoftBody;
import com.jme3.bullet.objects.PhysicsVehicle;
import com.jme3.bullet.objects.infos.Cluster;
import com.jme3.bullet.objects.infos.SoftBodyConfig;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.io.PrintStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.logging.Logger;
import jme3utilities.MyString;
import jme3utilities.Validate;
import jme3utilities.debug.Describer;
import jme3utilities.debug.Dumper;
import jme3utilities.math.MyQuaternion;
import jme3utilities.math.MyVector3f;

/**
 * Dump Minie data structures for debugging purposes.
 * <p>
 * The level of detail can be configured dynamically.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class PhysicsDumper extends Dumper {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final public static Logger logger
            = Logger.getLogger(PhysicsDumper.class.getName());
    // *************************************************************************
    // fields

    /**
     * enable dumping of clusters in soft bodies
     */
    private boolean dumpClustersInSofts = false;
    /**
     * enable dumping of joints in rigid bodies
     */
    private boolean dumpJointsInBodies = false;
    /**
     * enable dumping of joints in physics spaces
     */
    private boolean dumpJointsInSpaces = false;
    /**
     * enable dumping of motors in joints
     */
    private boolean dumpMotors = false;
    /**
     * enable dumping of nodes in clusters
     */
    private boolean dumpNodesInClusters = false;
    /**
     * enable dumping of nodes in soft bodies
     */
    private boolean dumpNodesInSofts = false;
    /**
     * enable dumping of collision objects in physics spaces
     */
    private boolean dumpPcos = true;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a PhysicsDumper that uses System.out for output.
     */
    public PhysicsDumper() {
        super();
        PhysicsDescriber newDescriber = new PhysicsDescriber();
        setDescriber(newDescriber);
    }

    /**
     * Instantiate a PhysicsDumper that uses the specified output stream.
     *
     * @param printStream output stream (not null)
     */
    public PhysicsDumper(PrintStream printStream) {
        super(printStream);
        PhysicsDescriber newDescriber = new PhysicsDescriber();
        setDescriber(newDescriber);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Dump the specified BulletAppState.
     *
     * @param appState the app state to dump (not null, unaffected)
     */
    public void dump(BulletAppState appState) {
        Validate.nonNull(appState, "app state");
        dumpBas(appState, "");
    }

    /**
     * Dump the specified PhysicsCharacter.
     *
     * @param character the character to dump (not null, unaffected)
     * @param indent (not null)
     */
    public void dump(PhysicsCharacter character, String indent) {
        Validate.nonNull(indent, "indent");

        long objectId = character.getObjectId();
        stream.printf("%n%sCharacter #%s", indent, Long.toHexString(objectId));

        String desc = getDescriber().describeUser(character);
        stream.print(desc);

        Vector3f location = character.getPhysicsLocation();
        String locString = MyVector3f.describe(location);
        stream.printf(" loc[%s]", locString);
    }

    /**
     * Dump the specified PhysicsGhostObject.
     *
     * @param ghost the ghost object to dump (not null, unaffected)
     * @param indent (not null)
     */
    public void dump(PhysicsGhostObject ghost, String indent) {
        Validate.nonNull(indent, "indent");

        long objectId = ghost.getObjectId();
        stream.printf("%n%sGhost #%s", indent, Long.toHexString(objectId));

        String desc = getDescriber().describeUser(ghost);
        stream.print(desc);

        Vector3f location = ghost.getPhysicsLocation(null);
        String locString = MyVector3f.describe(location);
        stream.printf(" loc[%s]", locString);
    }

    /**
     * Dump the specified PhysicsRigidBody.
     *
     * @param body the rigid body to dump (not null, unaffected)
     * @param indent (not null)
     */
    public void dump(PhysicsRigidBody body, String indent) {
        Validate.nonNull(indent, "indent");

        stream.printf("%n%sRigid ", indent);

        String desc = MyPco.describe(body);
        stream.print(desc);

        PhysicsDescriber describer = getDescriber();
        desc = describer.describeUser(body);
        stream.print(desc);

        Vector3f location = body.getPhysicsLocation(null);
        String locString = MyVector3f.describe(location);
        stream.printf(" loc[%s]", locString);

        Quaternion orientation = body.getPhysicsRotation(null);
        if (!MyQuaternion.isRotationIdentity(orientation)) {
            String orientText = MyQuaternion.describe(orientation);
            stream.printf(" orient[%s]", orientText);
        }

        // TODO dump friction and damping
        if (body.isDynamic()) {
            Vector3f velocity = body.getLinearVelocity(null);
            String velString = MyVector3f.describe(velocity);
            stream.printf(" v[%s]", velString);

            Vector3f gravity = body.getGravity(null);
            String graString = MyVector3f.describe(gravity);
            stream.printf(" grav[%s]", graString);

            stream.print(" ccd[mt=");
            float ccdMt = body.getCcdSquareMotionThreshold();
            stream.print(MyString.describe(ccdMt));
            if (ccdMt > 0f) {
                stream.print(" r=");
                float ccdR = body.getCcdSweptSphereRadius();
                stream.print(MyString.describe(ccdR));
            }
            stream.print(']');

            stream.print(" sleep[lt=");
            float linearThreshold = body.getLinearSleepingThreshold();
            stream.print(MyString.describe(linearThreshold));
            float angularThreshold = body.getAngularSleepingThreshold();
            stream.print(" at=");
            stream.print(MyString.describe(angularThreshold));
            if (body.isActive()) {
                float deactivationTime = body.getDeactivationTime();
                stream.print(" time=");
                stream.print(MyString.describe(deactivationTime));
            }
            stream.print(']');
        }

        stream.print(" #");
        long objectId = body.getObjectId();
        stream.printf(Long.toHexString(objectId));
        /*
         * 2nd line has the shape, scale, group info, and the number of joints.
         */
        CollisionShape shape = body.getCollisionShape();
        desc = describer.describe(shape);
        stream.printf("%n%s %s", indent, desc);

        Vector3f scale = shape.getScale(null);
        desc = describer.describeScale(scale);
        if (!desc.isEmpty()) {
            stream.print(' ');
            stream.print(desc);
        }

        desc = describer.describeGroups(body);
        stream.print(desc);

        int numJoints = body.countJoints();
        stream.printf(" with %d joint%s", numJoints,
                (numJoints == 1) ? "" : "s");
        if (dumpJointsInBodies && numJoints > 0) {
            dumpJoints(body, indent);
        }
    }

    /**
     * Dump the specified PhysicsSoftBody.
     *
     * @param body the soft body to dump (not null, unaffected)
     * @param indent (not null)
     */
    public void dump(PhysicsSoftBody body, String indent) {
        Validate.nonNull(indent, "indent");

        stream.printf("%n%sSoft ", indent);

        PhysicsDescriber describer = getDescriber();
        BoundingBox aabb = body.boundingBox(null);
        String desc = describer.describe(aabb);
        stream.print(desc);

        stream.print(" mass=");
        float mass = body.getMass();
        desc = MyString.describe(mass);
        stream.print(desc);

        stream.print(" marg=");
        float margin = body.margin();
        desc = MyString.describe(margin);
        stream.print(desc);

        stream.print(" #");
        long objectId = body.getObjectId();
        stream.print(Long.toHexString(objectId));

        stream.printf("%n%s  vol=", indent);
        float volume = body.volume();
        desc = MyString.describe(volume);
        stream.print(desc);

        stream.print(" wind[");
        Vector3f wind = body.windVelocity(null);
        desc = MyVector3f.describe(wind);
        stream.print(desc);
        stream.print(']');

        desc = describer.describeUser(body);
        stream.print(desc);

        int numLinks = body.countLinks();
        int numFaces = body.countFaces();
        int numTetras = body.countTetras();
        stream.printf(" with %d link%s, %d face%s, %d tetra%s",
                numLinks, (numLinks == 1) ? "" : "s",
                numFaces, (numFaces == 1) ? "" : "s",
                numTetras, (numTetras == 1) ? "" : "s");

        Quaternion orientation = body.getPhysicsRotation(null);
        if (!MyQuaternion.isRotationIdentity(orientation)) {
            desc = MyQuaternion.describe(orientation);
            stream.printf(" orient[%s]", desc);
        }
        /*
         * 3rd & 4th lines have the Config.
         */
        SoftBodyConfig config = body.getSoftConfig();
        desc = describer.describe1(config);
        stream.printf("%n%s %s", indent, desc);
        desc = describer.describe2(config);
        stream.printf("%n%s %s", indent, desc);
        /*
         * 5th line has the Material.
         */
        PhysicsSoftBody.Material material = body.getSoftMaterial();
        desc = describer.describe(material);
        stream.printf("%n%s %s", indent, desc);
        /*
         * 6th line has the world info.
         */
        SoftBodyWorldInfo info = body.getWorldInfo();
        desc = describer.describe(info);
        stream.printf("%n%s %s", indent, desc);
        /*
         * 7th line has the group info and number of anchors.
         */
        desc = describer.describeGroups(body);
        stream.printf("%n%s%s", indent, desc);

        int numJoints = body.countJoints();
        stream.printf(" with %d joint%s", numJoints,
                (numJoints == 1) ? "" : "s");
        if (dumpJointsInBodies && numJoints > 0) {
            dumpJoints(body, indent);
            stream.printf("%n%s", indent);
        } else {
            stream.print(',');
        }

        int numClusters = body.countClusters();
        stream.printf(" %d cluster%s", numClusters,
                (numClusters == 1) ? "" : "s");
        if (dumpClustersInSofts && numClusters > 0) {
            dumpClusters(body, indent);
        } else {
            stream.print(',');
        }

        int numNodes = body.countNodes();
        stream.printf(" %d node%s", numNodes, (numNodes == 1) ? "" : "s");
        if (dumpNodesInSofts && numNodes > 0) {
            dumpNodes(body, indent);
        }
    }

    /**
     * Dump the specified PhysicsSpace.
     *
     * @param space the PhysicsSpace to dump (not null, unaffected)
     */
    public void dump(PhysicsSpace space) {
        dump(space, "", null);
    }

    /**
     * Dump the specified PhysicsSpace with the specified filter.
     *
     * @param space the PhysicsSpace to dump (not null, unaffected)
     * @param indent (not null, may be empty)
     * @param filter determines which physics objects are dumped, or null to
     * dump all (unaffected)
     */
    public void dump(PhysicsSpace space, String indent,
            BulletDebugAppState.DebugAppStateFilter filter) {
        Validate.nonNull(indent, "indent");

        String type = space.getClass().getSimpleName();
        Collection<PhysicsCharacter> characters = space.getCharacterList();
        int numCharacters = characters.size();
        Collection<PhysicsGhostObject> ghosts = space.getGhostObjectList();
        int numGhosts = ghosts.size();
        stream.printf("%n%s%s with %d char%s, %d ghost%s, ",
                indent, type, numCharacters, (numCharacters == 1) ? "" : "s",
                numGhosts, (numGhosts == 1) ? "" : "s");

        Collection<PhysicsJoint> joints = space.getJointList();
        int numJoints = joints.size();
        stream.printf("%d joint%s, ", numJoints, (numJoints == 1) ? "" : "s");

        Collection<PhysicsRigidBody> rigidBodies = space.getRigidBodyList();
        int numRigids = rigidBodies.size();
        Collection<PhysicsSoftBody> softBodies = new ArrayList<>(0);
        if (space instanceof PhysicsSoftSpace) {
            softBodies = ((PhysicsSoftSpace) space).getSoftBodyList();
        }
        int numSofts = softBodies.size();
        Collection<PhysicsVehicle> vehicles = space.getVehicleList();
        int numVehicles = vehicles.size();
        stream.printf("%d rigid%s, %d soft%s, %d vehicle%s",
                numRigids, (numRigids == 1) ? "" : "s",
                numSofts, (numSofts == 1) ? "" : "s",
                numVehicles, (numVehicles == 1) ? "" : "s");

        long spaceId = space.getSpaceId();
        stream.printf(" #%s", Long.toHexString(spaceId));
        /*
         * 2nd line
         */
        PhysicsSpace.BroadphaseType bphase = space.getBroadphaseType();
        Vector3f gravity = space.getGravity(null);
        String gravString = MyVector3f.describe(gravity);
        stream.printf("%n%s bphase=%s grav[%s]", indent, bphase, gravString);

        int maxSubSteps = space.maxSubSteps();
        if (maxSubSteps == 0) {
            float maxTimeStep = space.maxTimeStep();
            String mtsDesc = MyString.describe(maxTimeStep);
            stream.printf(" timeStep[VAR max=%s]", mtsDesc);
        } else {
            float accuracy = space.getAccuracy();
            String accuDesc = MyString.describe(accuracy);
            stream.printf(" timeStep[%s maxSS=%d]", accuDesc, maxSubSteps);
        }
        /*
         * 3rd line
         */
        int numIterations = space.getSolverNumIterations();
        int rayTestFlags = space.getRayTestFlags();
        String rayTestText = RayTestFlag.describe(rayTestFlags);
        stream.printf("%n%s iters=%d rayTest=%s", indent, numIterations,
                rayTestText);

        if (bphase == PhysicsSpace.BroadphaseType.AXIS_SWEEP_3
                || bphase == PhysicsSpace.BroadphaseType.AXIS_SWEEP_3_32) {
            Vector3f worldMin = space.getWorldMin(null);
            String minString = MyVector3f.describe(worldMin);
            Vector3f worldMax = space.getWorldMax(null);
            String maxString = MyVector3f.describe(worldMax);
            stream.printf(" worldMin[%s] worldMax[%s]", minString, maxString);
        }
        /*
         * For soft spaces, 4th line has the world info.
         */
        PhysicsDescriber describer = getDescriber();
        if (space instanceof PhysicsSoftSpace) {
            SoftBodyWorldInfo info = ((PhysicsSoftSpace) space).getWorldInfo();
            String infoDesc = describer.describe(info);
            stream.printf("%n%s %s", indent, infoDesc);
        }

        if (dumpPcos) {
            String moreIndent = indent + indentIncrement();
            for (PhysicsCharacter character : characters) {
                if (filter == null || filter.displayObject(character)) {
                    dump(character, moreIndent);
                }
            }
            for (PhysicsGhostObject ghost : ghosts) {
                if (filter == null || filter.displayObject(ghost)) {
                    dump(ghost, moreIndent);
                }
            }
            for (PhysicsRigidBody rigid : rigidBodies) {
                if (filter == null || filter.displayObject(rigid)) {
                    dump(rigid, moreIndent);
                }
            }
            for (PhysicsSoftBody soft : softBodies) {
                if (filter == null || filter.displayObject(soft)) {
                    dump(soft, moreIndent);
                }
            }
            for (PhysicsVehicle vehicle : vehicles) {
                if (filter == null || filter.displayObject(vehicle)) {
                    dump(vehicle, moreIndent);
                }
            }
        }

        if (dumpJointsInSpaces) {
            dumpJoints(joints, indent, filter);
        }

        stream.println();
    }

    /**
     * Dump the specified PhysicsVehicle.
     *
     * @param vehicle the vehicle to dump (not null, unaffected)
     * @param indent (not null)
     */
    public void dump(PhysicsVehicle vehicle, String indent) {
        Validate.nonNull(indent, "indent");

        long objectId = vehicle.getObjectId();
        float mass = vehicle.getMass();
        stream.printf("%sVehicle #%s mass=%f", indent,
                Long.toHexString(objectId), mass);

        String desc = getDescriber().describeUser(vehicle);
        stream.print(desc);

        Vector3f location = vehicle.getPhysicsLocation(null);
        String locString = MyVector3f.describe(location);
        stream.printf(" loc[%s]", locString);
    }

    /**
     * Dump the specified BulletAppState.
     *
     * @param appState the app state to dump (not null, unaffected)
     * @param indent (not null)
     */
    public void dumpBas(BulletAppState appState, String indent) {
        Validate.nonNull(indent, "indent");

        String className = appState.getClass().getSimpleName();
        stream.print(className);

        if (appState.isEnabled()) {
            stream.print(" enabled ");

            if (!appState.isDebugEnabled()) {
                stream.print("NO");
            }
            stream.print("debug ");

            float speed = appState.getSpeed();
            String speedString = MyString.describe(speed);
            stream.printf("speed=%s", speedString);

            PhysicsSpace.BroadphaseType broadphaseType
                    = appState.getBroadphaseType();
            stream.printf(" bphase=%s", broadphaseType);

            PhysicsSpace space = appState.getPhysicsSpace();
            String moreIndent = indent + indentIncrement();
            dump(space, moreIndent, null);
        } else {
            stream.println(" disabled");
        }
    }

    /**
     * Test whether the specified dump flag is set.
     *
     * @param dumpFlag which flag to test (not null)
     * @return true if output is enabled, otherwise false
     */
    public boolean isEnabled(DumpFlags dumpFlag) {
        boolean result;

        switch (dumpFlag) {
            case Buckets:
                result = isDumpBucket();
                break;

            case ClustersInSofts:
                result = dumpClustersInSofts;
                break;

            case CullHints:
                result = isDumpCull();
                break;

            case JointsInBodies:
                result = dumpJointsInBodies;
                break;

            case JointsInSpaces:
                result = dumpJointsInSpaces;
                break;

            case MatParams:
                result = isDumpMatParam();
                break;

            case Motors:
                result = dumpMotors;
                break;

            case NodesInClusters:
                result = dumpNodesInClusters;
                break;

            case NodesInSofts:
                result = dumpNodesInSofts;
                break;

            case Overrides:
                result = isDumpOverride();
                break;

            case Pcos:
                result = dumpPcos;
                break;

            case ShadowModes:
                result = isDumpShadow();
                break;

            case Transforms:
                result = isDumpTransform();
                break;

            case UserData:
                result = isDumpUser();
                break;

            default:
                throw new IllegalArgumentException(dumpFlag.toString());
        }

        return result;
    }

    /**
     * Configure the specified dump flag.
     *
     * @param dumpFlag which flag to set (not null)
     * @param newValue true to enable output, false to disable it
     * @return this instance for chaining
     */
    public PhysicsDumper setEnabled(DumpFlags dumpFlag, boolean newValue) {
        switch (dumpFlag) {
            case Buckets:
                setDumpBucket(newValue);
                break;

            case ClustersInSofts:
                dumpClustersInSofts = newValue;
                break;

            case CullHints:
                setDumpCull(newValue);
                break;

            case JointsInBodies:
                dumpJointsInBodies = newValue;
                break;

            case JointsInSpaces:
                dumpJointsInSpaces = newValue;
                break;

            case MatParams:
                setDumpMatParam(newValue);
                break;

            case Motors:
                dumpMotors = newValue;
                break;

            case NodesInClusters:
                dumpNodesInClusters = newValue;
                break;

            case NodesInSofts:
                dumpNodesInSofts = newValue;
                break;

            case Overrides:
                setDumpOverride(newValue);
                break;

            case Pcos:
                dumpPcos = newValue;
                break;

            case ShadowModes:
                setDumpShadow(newValue);
                break;

            case Transforms:
                setDumpTransform(newValue);
                break;

            case UserData:
                setDumpUser(newValue);
                break;

            default:
                throw new IllegalArgumentException(dumpFlag.toString());
        }

        return this;
    }
    // *************************************************************************
    // Dumper methods

    /**
     * Create a deep copy of this dumper.
     *
     * @return a new instance, equivalent to this one, with its own Describer
     * @throws CloneNotSupportedException if the superclass isn't cloneable
     */
    @Override
    public PhysicsDumper clone() throws CloneNotSupportedException {
        PhysicsDumper clone = (PhysicsDumper) super.clone();
        return clone;
    }

    /**
     * Dump the specified AppState.
     *
     * @param appState the AppState to dump (not null, unaffected)
     * @param indent (not null)
     */
    @Override
    public void dump(AppState appState, String indent) {
        Validate.nonNull(appState, "app state");
        Validate.nonNull(indent, "indent");

        if (appState instanceof BulletAppState) {
            dumpBas((BulletAppState) appState, indent);
        } else {
            super.dump(appState, indent);
        }
    }

    /**
     * Access the Describer used by this Dumper.
     *
     * @return the pre-existing instance (not null)
     */
    @Override
    public PhysicsDescriber getDescriber() {
        Describer describer = super.getDescriber();
        PhysicsDescriber result = (PhysicsDescriber) describer;

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Generate a textual description of the indexed vector in the specified
     * buffer.
     *
     * @param buffer the buffer to read (not null, unaffected)
     * @param vectorIndex the index of the vector in the buffer (&ge;0)
     * @return descriptive text (not null, not empty)
     */
    private static String describeVector(FloatBuffer buffer, int vectorIndex) {
        int floatIndex = 3 * vectorIndex;
        float x = buffer.get(floatIndex);
        float y = buffer.get(floatIndex + 1);
        float z = buffer.get(floatIndex + 2);
        Vector3f vector = new Vector3f(x, y, z);
        String locString = MyVector3f.describe(vector);

        return locString;
    }

    /**
     * Dump all clusters in the specified soft body.
     *
     * @param softBody the body to dump (not null, unaffected)
     * @param indent (not null)
     */
    private void dumpClusters(PhysicsSoftBody softBody, String indent) {
        stream.print(':');
        FloatBuffer coms = softBody.copyClusterCenters(null);
        FloatBuffer masses = softBody.copyClusterMasses(null);
        int numClusters = softBody.countClusters();
        for (int clusterIndex = 0; clusterIndex < numClusters; ++clusterIndex) {
            stream.printf("%n%s  [%d] com[", indent, clusterIndex);
            String desc = describeVector(coms, clusterIndex);
            stream.print(desc);

            stream.print("] mass=");
            float mass = masses.get(clusterIndex);
            stream.print(MyString.describe(mass));

            stream.print(" damp[ang=");
            float angularDamping = softBody.get(Cluster.AngularDamping,
                    clusterIndex);
            stream.print(MyString.describe(angularDamping));

            stream.print(" lin=");
            float linearDamping = softBody.get(Cluster.LinearDamping,
                    clusterIndex);
            stream.print(MyString.describe(linearDamping));

            stream.print(" node=");
            float nodeDamping = softBody.get(Cluster.NodeDamping, clusterIndex);
            stream.print(MyString.describe(nodeDamping));

            stream.print("] match=");
            float matching = softBody.get(Cluster.Matching, clusterIndex);
            stream.print(MyString.describe(matching));

            stream.print(" scif=");
            float selfImpulse = softBody.get(Cluster.SelfImpulse, clusterIndex);
            stream.print(MyString.describe(selfImpulse));

            stream.print(" maxSci=");
            float maxSelfImpulse
                    = softBody.get(Cluster.MaxSelfImpulse, clusterIndex);
            stream.print(MyString.describe(maxSelfImpulse));

            int numNodes = softBody.countNodesInCluster(clusterIndex);
            stream.printf("  %d node%s", numNodes, (numNodes == 1) ? "" : "s");

            if (dumpMotors) {
                dumpNodesInCluster(softBody, clusterIndex);
            }
        }
        stream.printf("%n%s", indent);
    }

    /**
     * Dump the specified joints in a PhysicsSpace context.
     *
     * @param joints (not null, unaffected)
     * @param indent (not null, may be empty)
     * @param filter determines which physics objects are dumped, or null to
     * dump all (unaffected)
     */
    private void dumpJoints(Collection<PhysicsJoint> joints, String indent,
            BulletDebugAppState.DebugAppStateFilter filter) {
        PhysicsDescriber describer = getDescriber();
        String moreIndent = indent + indentIncrement();
        String mmIndent = moreIndent + indentIncrement();

        for (PhysicsJoint joint : joints) {
            if (filter == null || filter.displayObject(joint)) {
                String desc = describer.describeJointInSpace(joint);
                stream.printf("%n%s%s", moreIndent, desc);

                if (joint instanceof SixDofJoint) {
                    SixDofJoint sixDof = (SixDofJoint) joint;

                    desc = describer.describeAngular(sixDof);
                    stream.printf("%n%s %s", moreIndent, desc);
                    desc = describer.describeLinear(sixDof);
                    stream.printf("%n%s %s", moreIndent, desc);

                    if (dumpMotors) {
                        for (int axisIndex = 0; axisIndex < 3; ++axisIndex) {
                            String axisName = MyString.axisName(axisIndex);
                            stream.printf("%n%srot%s: ", mmIndent, axisName);
                            RotationalLimitMotor motor
                                    = sixDof.getRotationalLimitMotor(axisIndex);
                            desc = describer.describe(motor);
                            stream.print(desc);
                        }

                        TranslationalLimitMotor motor
                                = sixDof.getTranslationalLimitMotor();
                        for (int axisIndex = 0; axisIndex < 3; ++axisIndex) {
                            String axisName = MyString.axisName(axisIndex);
                            stream.printf("%n%stra%s: ", mmIndent, axisName);
                            desc = describer.describe(motor, axisIndex);
                            stream.print(desc);
                        }
                    }
                }
            }
        }
    }

    /**
     * Dump all joints in the specified body.
     *
     * @param body the body to dump (not null, unaffected)
     * @param indent (not null)
     */
    private void dumpJoints(PhysicsBody body, String indent) {
        stream.print(':');
        PhysicsJoint[] joints = body.listJoints();
        PhysicsDescriber describer = getDescriber();
        String moreIndent = indent + indentIncrement();
        for (PhysicsJoint joint : joints) {
            String desc = describer.describeJointInBody(joint, body);
            stream.printf("%n%s%s", moreIndent, desc);
        }
    }

    /**
     * Dump all nodes in the specified soft body.
     *
     * @param softBody the soft body to dump (not null, unaffected)
     * @param indent (not null)
     */
    private void dumpNodes(PhysicsSoftBody softBody, String indent) {
        stream.print(':');
        FloatBuffer locations = softBody.copyLocations(null);
        FloatBuffer masses = softBody.copyMasses(null);
        FloatBuffer velocities = softBody.copyVelocities(null);
        IntBuffer linkIndices = softBody.copyLinks(null);
        int numNodes = softBody.countNodes();
        int numLinks = softBody.countLinks();
        for (int nodeIndex = 0; nodeIndex < numNodes; ++nodeIndex) {
            int degree = frequency(linkIndices, 2 * numLinks, nodeIndex);
            float nodeMass = masses.get(nodeIndex);
            String locString = describeVector(locations, nodeIndex);
            String vString = describeVector(velocities, nodeIndex);
            stream.printf("%n%s  [%d] deg=%d mass=%s loc[%s] v[%s]",
                    indent, nodeIndex, degree, MyString.describe(nodeMass),
                    locString, vString);
        }
    }

    /**
     * Dump the indices of all nodes in the specified cluster.
     *
     * @param softBody the soft body to dump (not null, unaffected)
     * @param clusterIndex which cluster (&ge;0, &lt;numClusters)
     */
    private void dumpNodesInCluster(PhysicsSoftBody softBody,
            int clusterIndex) {
        IntBuffer nodeIndices = softBody.listNodesInCluster(clusterIndex, null);
        int numIndices = nodeIndices.capacity();
        int numNodesInBody = softBody.countNodes();
        if (numIndices == numNodesInBody) {
            stream.print("(all)");
            return;
        }
        /*
         * convert the IntBuffer to a BitSet
         */
        BitSet bitSet = new BitSet(numNodesInBody);
        for (int i = 0; i < numIndices; ++i) {
            int nodeIndex = nodeIndices.get(i);
            bitSet.set(nodeIndex);
        }

        stream.print('(');
        boolean addSeparators = false;
        for (int nodeIndex = 0; nodeIndex < numNodesInBody; ++nodeIndex) {
            if (bitSet.get(nodeIndex)) {
                if (addSeparators) {
                    stream.print(',');
                } else {
                    addSeparators = true;
                }
                int runLength = bitSet.nextClearBit(nodeIndex) - nodeIndex;
                if (runLength < 3) {
                    stream.printf("%d", nodeIndex);
                } else {
                    int endIndex = nodeIndex + runLength - 1;
                    stream.printf("%d-%d", nodeIndex, endIndex);
                    nodeIndex = endIndex;
                }
            }
        }
        stream.print(')');
    }

    /**
     * Count the number of times the specified integer value occurs in the
     * specified buffer. TODO move to utility class
     *
     * @param buffer the buffer to read (not null, unaffected)
     * @param bufferLength the number of integers in the buffer (&ge;0)
     * @param intValue the value to search for
     * @return the number of occurrences found (&ge;0)
     */
    private static int frequency(IntBuffer buffer, int bufferLength,
            int intValue) {
        int result = 0;
        for (int offset = 0; offset < bufferLength; ++offset) {
            int bufferValue = buffer.get(offset);
            if (bufferValue == intValue) {
                ++result;
            }
        }

        return result;
    }
}
