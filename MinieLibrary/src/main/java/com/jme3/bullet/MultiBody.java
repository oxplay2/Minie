/*
 * Copyright (c) 2020 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.bullet;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.objects.MultiBodyCollider;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.util.clone.Cloner;
import com.jme3.util.clone.JmeCloneable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.Validate;

/**
 * An articulated rigid body based on Bullet's btMultiBody. Uses Featherstone's
 * algorithm.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class MultiBody implements Comparable<MultiBody>, JmeCloneable, Savable {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final public static Logger logger
            = Logger.getLogger(MultiBody.class.getName());
    /**
     * field names for serialization
     */
    final private static String tagAngularDamping = "angularDamping";
    final private static String tagBaseAngularVelocity = "baseAngularVelocity";
    final private static String tagBaseCollider = "baseCollider";
    final private static String tagBaseInertia = "baseInertia";
    final private static String tagBaseLocation = "baseLocation";
    final private static String tagBaseMass = "baseMass";
    final private static String tagBaseVelocity = "baseVelocity";
    final private static String tagCanSleep = "canSleep";
    final private static String tagCollisionGroup = "collisionGroup";
    final private static String tagCollisionGroupsMask = "collisionGroupsMask";
    final private static String tagFixedBase = "fixedBase";
    final private static String tagGlobalVelocities = "globalVelocities";
    final private static String tagLinearDamping = "linearDamping";
    final private static String tagLinks = "links";
    final private static String tagNumConfigured = "numConfigured";
    final private static String tagNumLinks = "numLinks";
    final private static String tagRK4 = "RK4";
    // *************************************************************************
    // fields

    /**
     * number of links that have been configured (&ge;0)
     */
    private int numConfigured = 0;
    /**
     * unique identifier of the btMultiBody
     */
    private long nativeId;
    /**
     * collider for the base, or null if none
     */
    private MultiBodyCollider baseCollider = null;
    /**
     * references to configured links
     */
    private MultiBodyLink[] links;
    // *************************************************************************
    // constructors

    /**
     * No-argument constructor needed by SavableClassUtil.
     */
    protected MultiBody() {
    }

    /**
     * Instantiate a MultiBody.
     *
     * @param numLinks the desired number of links, not including the base
     * (&ge;0)
     * @param baseMass the desired mass of the base (in physics-space units,
     * &gt;0)
     * @param baseInertia the desired rotational inertia of the base (not null,
     * all elements positive)
     * @param fixedBase true &rarr; base is fixed, false &rarr; base can move
     * @param canSleep true &rarr; can sleep, false &rarr; won't sleep
     */
    public MultiBody(int numLinks, float baseMass, Vector3f baseInertia,
            boolean fixedBase, boolean canSleep) {
        Validate.nonNegative(numLinks, "number of links");
        Validate.positive(baseMass, "base mass");
        Validate.positive(baseInertia, "base inertia");

        nativeId = create(numLinks, baseMass, baseInertia, fixedBase, canSleep);
        assert nativeId != 0L;
        assert getNumLinks(nativeId) == numLinks;
        finalizeMultiDof(nativeId);

        links = new MultiBodyLink[numLinks];
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Add a collider for the base.
     *
     * @param shape (not null, alias created)
     * @return a new collider
     */
    public MultiBodyCollider addBaseCollider(CollisionShape shape) {
        Validate.nonNull(shape, "shape");
        assert baseCollider == null : baseCollider;

        baseCollider = new MultiBodyCollider(this, -1);
        long colliderId = baseCollider.getObjectId();
        setBaseCollider(nativeId, colliderId);

        baseCollider.attachShape(shape);
        assert getBaseCollider(nativeId) == colliderId;

        return baseCollider;
    }

    /**
     * Add an external force to the base.
     *
     * @param force the force to add (in physics-space coordinates, not null,
     * unaffected)
     */
    public void addBaseForce(Vector3f force) {
        Validate.finite(force, "force");
        addBaseForce(nativeId, force);
    }

    /**
     * Add an external torque to the base.
     *
     * @param torque the torque to add (in physics-space coordinates, not null,
     * unaffected)
     */
    public void addBaseTorque(Vector3f torque) {
        Validate.finite(torque, "torque");
        addBaseTorque(nativeId, torque);
    }

    /**
     * Determine the angular damping.
     *
     * @return the damping
     */
    public float angularDamping() {
        float result = getAngularDamping(nativeId);
        return result;
    }

    /**
     * Determine the total angular momentum of this MultiBody.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the momentum vector (either storeResult or a new vector, not
     * null)
     */
    public Vector3f angularMomentum(Vector3f storeResult) {
        Vector3f result = (storeResult == null) ? new Vector3f() : storeResult;
        getAngularMomentum(nativeId, result);
        return result;
    }

    /**
     * Determine the angular velocity of the base.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the angular-velocity vector (either storeResult or a new vector,
     * not null)
     */
    public Vector3f baseAngularVelocity(Vector3f storeResult) {
        Vector3f result = (storeResult == null) ? new Vector3f() : storeResult;
        getBaseOmega(nativeId, result);
        return result;
    }

    /**
     * Determine the net force on the base.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the force vector (either storeResult or a new vector, not null)
     */
    public Vector3f baseForce(Vector3f storeResult) {
        Vector3f result = (storeResult == null) ? new Vector3f() : storeResult;
        getBaseForce(nativeId, result);
        return result;
    }

    /**
     * Determine the rotational inertia of the base.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the principal (diagonal) components of the inertia tensor (in the
     * base's local coordinates, either storeResult or a new vector, not null)
     */
    public Vector3f baseInertia(Vector3f storeResult) {
        Vector3f result = (storeResult == null) ? new Vector3f() : storeResult;
        getBaseInertia(nativeId, result);
        return result;
    }

    /**
     * Determine the location of the base's center of mass.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the location vector (in physics-space coordinates, either
     * storeResult or a new vector, not null)
     */
    public Vector3f baseLocation(Vector3f storeResult) {
        Vector3f result = (storeResult == null) ? new Vector3f() : storeResult;
        getBasePos(nativeId, result);
        return result;
    }

    /**
     * Determine the mass of the base.
     *
     * @return the mass (in physics-space units, &gt;0)
     */
    public float baseMass() {
        float result = getBaseMass(nativeId);
        return result;
    }

    /**
     * Determine the orientation of the base.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the orientation (either storeResult or a new instance, not null)
     */
    public Quaternion baseOrientation(Quaternion storeResult) {
        Quaternion result
                = (storeResult == null) ? new Quaternion() : storeResult;
        getWorldToBaseRot(nativeId, result);
        return result;
    }

    /**
     * Determine the net torque on the base.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the torque vector (either storeResult or a new vector, not null)
     */
    public Vector3f baseTorque(Vector3f storeResult) {
        Vector3f result = (storeResult == null) ? new Vector3f() : storeResult;
        getBaseTorque(nativeId, result);
        return result;
    }

    /**
     * Determine the transform of the base.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the transform from local coordinates to physics-space coordinates
     * (either storeResult or a new instance, not null, scale=1)
     */
    public Transform baseTransform(Transform storeResult) {
        Transform result
                = (storeResult == null) ? new Transform() : storeResult;
        getBaseWorldTransform(nativeId, result);
        return result;
    }

    /**
     * Determine the linear velocity of the base's center of mass.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the velocity vector (in physics-space coordinates, either
     * storeResult or a new vector, not null)
     */
    public Vector3f baseVelocity(Vector3f storeResult) {
        Vector3f result = (storeResult == null) ? new Vector3f() : storeResult;
        getBaseVel(nativeId, result);
        return result;
    }

    /**
     * Test whether this MultiBody can sleep.
     *
     * @return true if it can sleep, otherwise false
     */
    public boolean canSleep() {
        boolean result = getCanSleep(nativeId);
        return result;
    }

    /**
     * Test whether this MultiBody can wake up.
     *
     * @return true if it can wake up, otherwise false
     */
    public boolean canWakeup() {
        boolean result = getCanWakeup(nativeId);
        return result;
    }

    /**
     * Clear all constraint forces.
     */
    public void clearConstraintForces() {
        clearConstraintForces(nativeId);
    }

    /**
     * Clear all external forces and torques.
     */
    public void clearForcesAndTorques() {
        clearForcesAndTorques(nativeId);
    }

    /**
     * Zero out all velocities.
     */
    public void clearVelocities() {
        clearVelocities(nativeId);
    }

    /**
     * Read the set of collision groups with which this multibody can collide.
     *
     * @return bit mask
     */
    public int collideWithGroups() {
        int result = getCollideWithGroups(nativeId);
        return result;
    }

    /**
     * Read the collision group of this multibody.
     *
     * @return the collision group (bit mask with exactly one bit set)
     */
    public int collisionGroup() {
        int result = getCollisionGroup(nativeId);
        assert Integer.bitCount(result) == 1 : result;
        return result;
    }

    /**
     * Configure a new link that is fixed to its parent.
     *
     * @param mass the desired mass of the link (&gt;0)
     * @param inertia the desired moment of inertia of the link (not null,
     * unaffected)
     * @param parent the parent link, or null if parented by the base
     * @param orientation the orientation of the link relative to its parent
     * (not null, unaffected)
     * @param parent2Pivot the offset of the pivot from the parent's center of
     * mass (not null, unaffected)
     * @param pivot2Link the offset of the child's center of mass from the pivot
     * (not null, unaffected)
     * @return a new link
     */
    public MultiBodyLink configureFixedLink(float mass, Vector3f inertia,
            MultiBodyLink parent, Quaternion orientation, Vector3f parent2Pivot,
            Vector3f pivot2Link) {
        Validate.positive(mass, "mass");
        Validate.positive(inertia, "inertia");
        Validate.nonNull(orientation, "orientation");
        Validate.nonNull(parent2Pivot, "parent to pivot offset");
        Validate.nonNull(pivot2Link, "pivot to link offset");
        assert numConfigured < links.length;

        int parentIndex = (parent == null) ? -1 : parent.index();
        setupFixed(nativeId, numConfigured, mass, inertia, parentIndex,
                orientation, parent2Pivot, pivot2Link);
        MultiBodyLink result = configureLink();

        return result;
    }

    /**
     * Configure a link that is joined to its parent with a planar joint.
     *
     * @param mass the desired mass of the link (&gt;0)
     * @param inertia the desired moment of inertia of the link (not null,
     * unaffected)
     * @param parent the parent link, or null if parented by the base
     * @param orientation the orientation of the link relative to its parent
     * (not null, unaffected)
     * @param axis the axis of rotation, which is also the plane's normal vector
     * (not null, unaffected)
     * @param parent2Link the offset of the child's center of mass from the
     * parent's center of mass (not null, unaffected)
     * @param disableCollision true to ignore collisions between the link and
     * its parent
     * @return a new link
     */
    public MultiBodyLink configurePlanarLink(float mass, Vector3f inertia,
            MultiBodyLink parent, Quaternion orientation, Vector3f axis,
            Vector3f parent2Link, boolean disableCollision) {
        Validate.positive(mass, "mass");
        Validate.positive(inertia, "inertia");
        Validate.nonNull(orientation, "orientation");
        Validate.nonNull(axis, "axis");
        Validate.nonNull(parent2Link, "parent to link offset");
        assert numConfigured < links.length;

        int parentIndex = (parent == null) ? -1 : parent.index();
        setupPlanar(nativeId, numConfigured, mass, inertia, parentIndex,
                orientation, axis, parent2Link, disableCollision);
        MultiBodyLink result = configureLink();

        return result;
    }

    /**
     * Configure a link that is joined to its parent with a prismatic joint.
     *
     * @param mass the desired mass of the link (&gt;0)
     * @param inertia the desired moment of inertia of the link (not null,
     * unaffected)
     * @param parent the parent link, or null if parented by the base
     * @param orientation the orientation of the link relative to its parent
     * (not null, unaffected)
     * @param axis the axis of rotation (not null, unaffected)
     * @param parent2Pivot the offset of the pivot from the parent's center of
     * mass (not null, unaffected)
     * @param pivot2Link the offset of the child's center of mass from the pivot
     * (not null, unaffected)
     * @param disableCollision true to ignore collisions between the link and
     * its parent
     * @return a new link
     */
    public MultiBodyLink configurePrismaticLink(float mass, Vector3f inertia,
            MultiBodyLink parent, Quaternion orientation, Vector3f axis,
            Vector3f parent2Pivot, Vector3f pivot2Link,
            boolean disableCollision) {
        Validate.positive(mass, "mass");
        Validate.positive(inertia, "inertia");
        Validate.nonNull(orientation, "orientation");
        Validate.nonNull(axis, "axis");
        Validate.nonNull(parent2Pivot, "parent to pivot offset");
        Validate.nonNull(pivot2Link, "pivot to link offset");
        assert numConfigured < links.length;

        int parentIndex = (parent == null) ? -1 : parent.index();
        setupPrismatic(nativeId, numConfigured, mass, inertia, parentIndex,
                orientation, axis, parent2Pivot, pivot2Link, disableCollision);
        MultiBodyLink result = configureLink();

        return result;
    }

    /**
     * Configure a link that is joined to its parent with a revolute joint.
     *
     * @param mass the desired mass of the link (&gt;0)
     * @param inertia the desired moment of inertia of the link (not null,
     * unaffected)
     * @param parent the parent link, or null if parented by the base
     * @param orientation the orientation of the link relative to its parent
     * (not null, unaffected)
     * @param axis the axis of rotation (not null, unaffected)
     * @param parent2Pivot the offset of the pivot from the parent's center of
     * mass (not null, unaffected)
     * @param pivot2Link the offset of the child's center of mass from the pivot
     * (not null, unaffected)
     * @param disableCollision true to ignore collisions between the link and
     * its parent
     * @return a new link
     */
    public MultiBodyLink configureRevoluteLink(float mass, Vector3f inertia,
            MultiBodyLink parent, Quaternion orientation, Vector3f axis,
            Vector3f parent2Pivot, Vector3f pivot2Link,
            boolean disableCollision) {
        Validate.positive(mass, "mass");
        Validate.positive(inertia, "inertia");
        Validate.nonNull(orientation, "orientation");
        Validate.nonNull(axis, "axis");
        Validate.nonNull(parent2Pivot, "parent to pivot offset");
        Validate.nonNull(pivot2Link, "pivot to link offset");
        assert numConfigured < links.length;

        int parentIndex = (parent == null) ? -1 : parent.index();
        setupRevolute(nativeId, numConfigured, mass, inertia, parentIndex,
                orientation, axis, parent2Pivot, pivot2Link, disableCollision);
        MultiBodyLink result = configureLink();

        return result;
    }

    /**
     * Configure a link that is joined to its parent with a spherical joint.
     *
     * @param mass the desired mass of the link (&gt;0)
     * @param inertia the desired moment of inertia of the link (not null,
     * unaffected)
     * @param parent the parent link, or null if parented by the base
     * @param orientation the orientation of the link relative to its parent
     * (not null, unaffected)
     * @param parent2Pivot the offset of the pivot from the parent's center of
     * mass (not null, unaffected)
     * @param pivot2Link the offset of the child's center of mass from the pivot
     * (not null, unaffected)
     * @param disableCollision true to ignore collisions between the link and
     * its parent
     * @return a new link
     */
    public MultiBodyLink configureSphericalLink(float mass, Vector3f inertia,
            MultiBodyLink parent, Quaternion orientation, Vector3f parent2Pivot,
            Vector3f pivot2Link, boolean disableCollision) {
        Validate.positive(mass, "mass");
        Validate.positive(inertia, "inertia");
        Validate.nonNull(orientation, "orientation");
        Validate.nonNull(parent2Pivot, "parent to pivot offset");
        Validate.nonNull(pivot2Link, "pivot to link offset");
        assert numConfigured < links.length;

        int parentIndex = (parent == null) ? -1 : parent.index();
        setupSpherical(nativeId, numConfigured, mass, inertia, parentIndex,
                orientation, parent2Pivot, pivot2Link, disableCollision);
        MultiBodyLink result = configureLink();

        return result;
    }

    /**
     * Test whether the specified collider is part of this MultiBody.
     *
     * @param collider the collider to search for (not null, unaffected)
     * @return true if included, otherwise false
     */
    public boolean contains(MultiBodyCollider collider) {
        boolean result = false;

        for (MultiBodyLink link : links) {
            if (link != null && link.getCollider() == collider) {
                result = true;
                break;
            }
        }

        if (!result && collider == baseCollider) {
            result = true;
        }

        return result;
    }

    /**
     * Count the configured links in this MultiBody.
     *
     * @return the count (&ge;0)
     */
    public int countConfiguredLinks() {
        assert numConfigured >= 0 : numConfigured;
        return numConfigured;
    }

    /**
     * Count the degrees of freedom.
     *
     * @return the count (&ge;0)
     */
    public int countDofs() {
        int result = getNumDofs(nativeId);
        return result;
    }

    /**
     * Count the position variables in this MultiBody.
     *
     * @return the count (&ge;0)
     */
    public int countPositionVariables() {
        int result = getNumPosVars(nativeId);
        return result;
    }

    /**
     * Access the collider for the base.
     *
     * @return the pre-existing instance, or null if none
     */
    public MultiBodyCollider getBaseCollider() {
        if (baseCollider == null) {
            assert getBaseCollider(nativeId) == 0L;
        } else {
            assert getBaseCollider(nativeId) == baseCollider.getObjectId();
        }

        return baseCollider;
    }

    /**
     * Access the index link of this MultiBody.
     *
     * @param linkIndex (&ge;0, &lt;numConfigured)
     * @return the pre-existing instance (not null)
     */
    public MultiBodyLink getLink(int linkIndex) {
        Validate.inRange(linkIndex, "link index", 0, numConfigured - 1);
        MultiBodyLink result = links[linkIndex];

        assert result != null;
        return result;
    }

    /**
     * Test whether this MultiBody has a fixed base.
     *
     * @return true &rarr; fixed, false &rarr; movable
     */
    public boolean hasFixedBase() {
        boolean result = hasFixedBase(nativeId);
        return result;
    }

    /**
     * Test whether this MultiBody uses global variables.
     *
     * @return true if using global variables, otherwise false
     */
    public boolean isUsingGlobalVelocities() {
        boolean result = isUsingGlobalVelocities(nativeId);
        return result;
    }

    /**
     * Test whether this MultiBody uses the gyro term.
     *
     * @return true if using the gyro term, otherwise false
     */
    public boolean isUsingGyroTerm() {
        boolean result = getUseGyroTerm(nativeId);
        return result;
    }

    /**
     * Test whether this MultiBody uses RK4 integration.
     *
     * @return true if using RK4, otherwise false
     */
    public boolean isUsingRK4() {
        boolean result = isUsingRK4Integration(nativeId);
        return result;
    }

    /**
     * Determine the total kinetic energy of this MultiBody.
     *
     * @return the energy (&ge;0)
     */
    public float kineticEnergy() {
        float result = getKineticEnergy(nativeId);
        return result;
    }

    /**
     * Determine the linear damping.
     *
     * @return the damping
     */
    public float linearDamping() {
        float result = getLinearDamping(nativeId);
        return result;
    }

    /**
     * Enumerate the colliders in this MultiBody.
     *
     * @return a new collection of pre-existing instances
     */
    public List<MultiBodyCollider> listColliders() {
        int capacity = numConfigured + 1;
        List<MultiBodyCollider> result = new ArrayList<>(capacity);

        if (baseCollider != null) {
            result.add(baseCollider);
        }
        for (MultiBodyLink link : links) {
            if (link != null) {
                MultiBodyCollider collider = link.getCollider();
                if (collider != null) {
                    result.add(collider);
                }
            }
        }

        return result;
    }

    /**
     * Determine the maximum applied impulse.
     *
     * @return the impulse
     */
    public float maxAppliedImpulse() {
        float result = getMaxAppliedImpulse(nativeId);
        return result;
    }

    /**
     * Determine the maximum coordinate velocity.
     *
     * @return the velocity
     */
    public float maxCoordinateVelocity() {
        float result = getMaxCoordinateVelocity(nativeId);
        return result;
    }

    /**
     * Determine the unique identifier of the native object.
     *
     * @return the ID (not zero)
     */
    final public long nativeId() {
        assert nativeId != 0L;
        return nativeId;
    }

    /**
     * Alter the angular velocity of the base.
     *
     * @param angularVelocity the desired angular-velocity vector (in
     * physics-space coordinates, not null, unaffected, default=(0,0,0))
     */
    public void setBaseAngularVelocity(Vector3f angularVelocity) {
        Validate.finite(angularVelocity, "angular velocity");
        setBaseOmega(nativeId, angularVelocity);
    }

    /**
     * Alter the location of the base's center of mass.
     *
     * @param location the desired location vector (in physics-space
     * coordinates, not null, unaffected, default=(0,0,0))
     */
    public void setBaseLocation(Vector3f location) {
        Validate.finite(location, "location");
        setBasePos(nativeId, location);
    }

    /**
     * Alter the orientation of the base.
     *
     * @param orientation the desired orientation (in physics-space coordinates,
     * not null, unaffected)
     */
    public void setBaseOrientation(Quaternion orientation) {
        Validate.nonNull(orientation, "orientation");
        setWorldToBaseRot(nativeId, orientation);
    }

    /**
     * Alter the transform of the base.
     *
     * @param transform the desired transform from local coordinates to
     * physics-space coordinates (not null, unaffected, scale ignored)
     */
    public void setBaseTransform(Transform transform) {
        Validate.nonNull(transform, "transform");
        setBaseWorldTransform(nativeId, transform);
    }

    /**
     * Alter the linear velocity of the base.
     *
     * @param velocity the desired velocity vector (in physics-space
     * coordinates, not null, unaffected, default=(0,0,0))
     */
    public void setBaseVelocity(Vector3f velocity) {
        Validate.finite(velocity, "velocity");
        setBaseVel(nativeId, velocity);
    }

    /**
     * Directly alter the collision groups with which this multibody can
     * collide.
     *
     * @param groups desired groups, ORed together (bit mask,
     * default=COLLISION_GROUP_01)
     */
    public void setCollideWithGroups(int groups) {
        setCollideWithGroups(nativeId, groups);
    }

    /**
     * Alter which collision group this multibody belongs to.
     * <p>
     * Groups are represented by integer bit masks with exactly one bit set.
     * Pre-made variables are available in PhysicsCollisionObject.
     * <p>
     * Two objects can collide only if one of them has the collisionGroup of the
     * other in its collideWithGroups set.
     *
     * @param group the collisionGroup to apply (bit mask with exactly one bit
     * set, default=COLLISION_GROUP_01)
     */
    public void setCollisionGroup(int group) {
        Validate.require(Integer.bitCount(group) == 1, "exactly one bit set");
        setCollisionGroup(nativeId, group);
    }

    /**
     * Determine the ID of the PhysicsSpace to which this multibody is added.
     *
     * @return the ID, or zero if not in any space
     */
    public long spaceId() {
        long spaceId = getSpace(nativeId);
        return spaceId;
    }

    /**
     * Alter whether this MultiBody uses global velocities.
     *
     * @param setting true to use global velocities (default=false)
     */
    public void useGlobalVelocities(boolean setting) {
        useGlobalVelocities(nativeId, setting);
    }

    /**
     * Alter whether this MultiBody uses RK4 integration.
     *
     * @param setting true to use RK4 (default=false)
     */
    public void useRK4(boolean setting) {
        useRK4Integration(nativeId, setting);
    }
    // *************************************************************************
    // Comparable methods

    /**
     * Compare (by ID) with another MultiBody.
     *
     * @param other (not null, unaffected)
     * @return 0 if the objects have the same ID; negative if this comes before
     * other; positive if this comes after other
     */
    @Override
    public int compareTo(MultiBody other) {
        long otherId = other.nativeId();
        int result = Long.compare(nativeId, otherId);

        return result;
    }
    // *************************************************************************
    // JmeCloneable methods

    /**
     * Callback from {@link com.jme3.util.clone.Cloner} to convert this
     * shallow-cloned object into a deep-cloned one, using the specified Cloner
     * and original to resolve copied fields.
     *
     * @param cloner the Cloner that's cloning this object (not null)
     * @param original the instance from which this object was shallow-cloned
     * (not null, unaffected)
     */
    @Override
    public void cloneFields(Cloner cloner, Object original) {
        int numLinks = links.length;
        float baseMass = baseMass();
        Vector3f baseInertia = baseInertia(null);
        boolean fixedBase = hasFixedBase();
        boolean canSleep = canSleep();
        nativeId = create(numLinks, baseMass, baseInertia, fixedBase, canSleep);
        assert nativeId != 0L;
        assert getNumLinks(nativeId) == numLinks;
        MultiBody old = (MultiBody) original;

        if (baseCollider != null) {
            CollisionShape baseShape = baseCollider.getCollisionShape();
            baseCollider = null;
            baseShape = cloner.clone(baseShape);
            addBaseCollider(baseShape);
            assert getBaseCollider(nativeId) == baseCollider.getObjectId();
            baseCollider.copyPcoProperties(old.baseCollider);
        }

        numConfigured = 0;
        links = new MultiBodyLink[numLinks];
        for (int i = 0; i < numLinks; ++i) {
            links[i] = configureClonedLink(old.links[i]);
        }
        finalizeMultiDof(nativeId);

        setCollideWithGroups(old.collideWithGroups());
        setCollisionGroup(old.collisionGroup());
    }

    /**
     * Create a shallow clone for the JME cloner. Note that the cloned MultiBody
     * won't be added to any PhysicsSpace, even if the original was.
     *
     * @return a new instance
     */
    @Override
    public MultiBody jmeClone() {
        try {
            MultiBody clone = (MultiBody) super.clone();
            return clone;
        } catch (CloneNotSupportedException exception) {
            throw new RuntimeException(exception);
        }
    }
    // *************************************************************************
    // Savable methods

    /**
     * De-serialize this object from the specified importer, for example when
     * loading from a J3O file.
     *
     * @param importer (not null)
     * @throws IOException from the importer
     */
    @Override
    public void read(JmeImporter importer) throws IOException {
        InputCapsule capsule = importer.getCapsule(this);

        Vector3f baseInertia
                = (Vector3f) capsule.readSavable(tagBaseInertia, null);
        float baseMass = capsule.readFloat(tagBaseMass, 1f);
        boolean canSleep = capsule.readBoolean(tagCanSleep, true);
        boolean fixedBase = capsule.readBoolean(tagFixedBase, false);
        int numLinks = capsule.readInt(tagNumLinks, 0);
        nativeId = create(numLinks, baseMass, baseInertia, fixedBase, canSleep);

        if (baseCollider != null) {
            CollisionShape baseShape = baseCollider.getCollisionShape();
            baseCollider = null;
            addBaseCollider(baseShape);
        }

        MultiBodyLink[] originalLinks = links;
        links = new MultiBodyLink[numLinks];
        numConfigured = capsule.readInt(tagNumConfigured, 0);
        for (int i = 0; i < numConfigured; ++i) {
            configureClonedLink(originalLinks[i]);
        }
        finalizeMultiDof(nativeId);

        int collisionGroup = capsule.readInt(tagCollisionGroup,
                PhysicsCollisionObject.COLLISION_GROUP_01);
        setCollisionGroup(collisionGroup);

        int collideWithGroups = capsule.readInt(tagCollisionGroupsMask,
                PhysicsCollisionObject.COLLISION_GROUP_01);
        setCollideWithGroups(collideWithGroups);

        Vector3f angularVelocity
                = (Vector3f) capsule.readSavable(tagBaseAngularVelocity, null);
        setBaseAngularVelocity(angularVelocity);

        Vector3f location
                = (Vector3f) capsule.readSavable(tagBaseLocation, null);
        setBaseLocation(location);

        Vector3f velocity
                = (Vector3f) capsule.readSavable(tagBaseVelocity, null);
        setBaseVelocity(velocity);
    }

    /**
     * Serialize this object to the specified exporter, for example when saving
     * to a J3O file.
     *
     * @param exporter (not null)
     * @throws IOException from the exporter
     */
    @Override
    public void write(JmeExporter exporter) throws IOException {
        OutputCapsule capsule = exporter.getCapsule(this);

        capsule.write(angularDamping(), tagAngularDamping, 0.04f);
        capsule.write(baseAngularVelocity(null), tagBaseAngularVelocity, null);
        capsule.write(getBaseCollider(), tagBaseCollider, null);
        capsule.write(baseInertia(null), tagBaseInertia, null);
        capsule.write(baseLocation(null), tagBaseLocation, null);
        capsule.write(baseMass(), tagBaseMass, 1f);
        capsule.write(baseVelocity(null), tagBaseVelocity, null);
        capsule.write(canSleep(), tagCanSleep, true);
        capsule.write(collisionGroup(), tagCollisionGroup,
                PhysicsCollisionObject.COLLISION_GROUP_01);
        capsule.write(collideWithGroups(), tagCollisionGroupsMask,
                PhysicsCollisionObject.COLLISION_GROUP_01);
        capsule.write(hasFixedBase(), tagFixedBase, false);
        capsule.write(isUsingGlobalVelocities(), tagGlobalVelocities, false);
        capsule.write(linearDamping(), tagLinearDamping, 0.04f);

        int numLinks = links.length;
        Savable[] savableLinks = new Savable[numLinks];
        for (int i = 0; i < numLinks; ++i) {
            savableLinks[i] = (Savable) links[i];
        }
        capsule.write(savableLinks, tagLinks, null);

        capsule.write(numLinks, tagNumLinks, 0);
        capsule.write(isUsingRK4(), tagRK4, false);
    }
    // *************************************************************************
    // Object methods

    /**
     * Finalize this MultiBody just before it is destroyed. Should be invoked
     * only by a subclass or by the garbage collector.
     *
     * @throws Throwable ignored by the garbage collector
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        logger.log(Level.FINE, "Finalizing {0}.", this);
        finalizeNative(nativeId);
    }

    /**
     * Represent this MultiBody as a String.
     *
     * @return a descriptive string of text (not null, not empty)
     */
    @Override
    public String toString() {
        String result = getClass().getSimpleName();
        result += "#" + Long.toHexString(nativeId);

        return result;
    }
    // *************************************************************************
    // private Java methods

    /**
     * Configure a new link that's just been set up.
     *
     * @return the new link (not null)
     */
    private MultiBodyLink configureLink() {
        int linkIndex = numConfigured;
        ++numConfigured;

        finalizeMultiDof(nativeId);

        MultiBodyLink result = new MultiBodyLink(this, linkIndex);
        links[linkIndex] = result;

        return result;
    }

    /**
     * Configure a new link that's a copy of the specified link, except it's
     * part of this MultiBody.
     *
     * @param original (not null, unaffected)
     * @return a new link of the same type
     */
    private MultiBodyLink configureClonedLink(MultiBodyLink original) {
        MultiBodyJointType jointType = original.jointType();
        float mass = original.mass();
        Vector3f inertia = original.inertia(null);
        Quaternion orientation = original.orientation(null);
        boolean disableCollision = !original.isCollisionWithParent();

        MultiBodyLink origParent = original.getParentLink();
        MultiBodyLink parent = null;
        if (origParent != null) {
            int parentIndex = origParent.index();
            parent = links[parentIndex];
        }

        Vector3f axis, parent2Link, parent2Pivot, pivot2Link;
        MultiBodyLink result;
        switch (jointType) {
            case Fixed:
                assert disableCollision == true;
                parent2Pivot = original.parent2Pivot(null);
                pivot2Link = original.pivot2Link(null);
                result = configureFixedLink(mass, inertia, parent, orientation,
                        parent2Pivot, pivot2Link);
                break;

            case Planar:
                axis = original.axis(null);
                parent2Link = original.parent2Link(null);
                result = configurePlanarLink(mass, inertia, parent, orientation,
                        axis, parent2Link, disableCollision);
                break;

            case Prismatic:
                axis = original.axis(null);
                parent2Pivot = original.parent2Pivot(null);
                pivot2Link = original.pivot2Link(null);
                result = configurePrismaticLink(mass, inertia, parent,
                        orientation, axis, parent2Pivot, pivot2Link,
                        disableCollision);
                break;

            case Revolute:
                axis = original.axis(null);
                parent2Pivot = original.parent2Pivot(null);
                pivot2Link = original.pivot2Link(null);
                result = configureRevoluteLink(mass, inertia, parent,
                        orientation, axis, parent2Pivot, pivot2Link,
                        disableCollision);
                break;

            case Spherical:
                parent2Pivot = original.parent2Pivot(null);
                pivot2Link = original.pivot2Link(null);
                result = configureSphericalLink(mass, inertia, parent,
                        orientation, parent2Pivot, pivot2Link,
                        disableCollision);
                break;

            default:
                throw new IllegalStateException("jointType = " + jointType);
        }

        return result;
    }
    // *************************************************************************
    // native methods

    native private void addBaseForce(long multiBodyId, Vector3f forceVector);

    native private void addBaseTorque(long multiBodyId, Vector3f torqueVector);

    native private void clearConstraintForces(long multiBodyId);

    native private void clearForcesAndTorques(long multiBodyId);

    native private void clearVelocities(long multiBodyId);

    native private long create(int numLinks, float baseMass,
            Vector3f baseInertiaVector, boolean fixedBase, boolean canSleep);

    native private void finalizeMultiDof(long multiBodyId);

    native private void finalizeNative(long multiBodyId);

    native private float getAngularDamping(long multiBodyId);

    native private void getAngularMomentum(long multiBodyId,
            Vector3f storeVector);

    native private long getBaseCollider(long multiBodyId);

    native private void getBaseForce(long multiBodyId, Vector3f storeVector);

    native private void getBaseInertia(long multiBodyId, Vector3f storeVector);

    native private float getBaseMass(long multiBodyId);

    native private void getBaseOmega(long multiBodyId, Vector3f storeVector);

    native private void getBasePos(long multiBodyId, Vector3f storeVector);

    native private void getBaseTorque(long multiBodyId, Vector3f storeVector);

    native private void getBaseVel(long multiBodyId, Vector3f storeVector);

    native private void getBaseWorldTransform(long multiBodyId,
            Transform storeTransform);

    native private boolean getCanSleep(long multiBodyId);

    native private boolean getCanWakeup(long multiBodyId);

    native private int getCollideWithGroups(long multiBodyId);

    native private int getCollisionGroup(long multiBodyId);

    native private float getKineticEnergy(long multiBodyId);

    native private float getLinearDamping(long multiBodyId);

    native private float getMaxAppliedImpulse(long multiBodyId);

    native private float getMaxCoordinateVelocity(long multiBodyId);

    native private int getNumDofs(long multiBodyId);

    native private int getNumLinks(long multiBodyId);

    native private int getNumPosVars(long multiBodyId);

    native private long getSpace(long multiBodyId);

    native private boolean getUseGyroTerm(long multiBodyId);

    native private void getWorldToBaseRot(long multiBodyId,
            Quaternion storeQuaternion);

    native private boolean hasFixedBase(long multiBodyId);

    native private boolean isUsingGlobalVelocities(long multiBodyId);

    native private boolean isUsingRK4Integration(long multiBodyId);

    native private void setBaseCollider(long multiBodyId, long colliderId);

    native private void setBaseOmega(long multiBodyId,
            Vector3f angularVelocityVector);

    native private void setBasePos(long multiBodyId, Vector3f positionVector);

    native private void setBaseVel(long multiBodyId, Vector3f velocityVector);

    native private void setBaseWorldTransform(long multiBodyId,
            Transform transform);

    native private void setCollideWithGroups(long multiBodyId,
            int collisionGroups);

    native private void setCollisionGroup(long multiBodyId, int collisionGroup);

    native private void setupFixed(long multiBodyId, int linkIndex,
            float mass, Vector3f inertiaVector, int parentLinkIndex,
            Quaternion parent2LinkQuaternion, Vector3f parent2PivotVector,
            Vector3f pivot2LinkVector);

    native private void setupPlanar(long multiBodyId, int linkIndex,
            float mass, Vector3f inertiaVector, int parentLinkIndex,
            Quaternion parent2LinkQuaternion, Vector3f axisVector,
            Vector3f parent2LinkVector, boolean disableParentCollision);

    native private void setupPrismatic(long multiBodyId, int linkIndex,
            float mass, Vector3f inertiaVector, int parentLinkIndex,
            Quaternion parent2LinkQuaternion, Vector3f axisVector,
            Vector3f parent2PivotVector, Vector3f pivot2LinkVector,
            boolean disableParentCollision);

    native private void setupRevolute(long multiBodyId, int linkIndex,
            float mass, Vector3f inertiaVector, int parentLinkIndex,
            Quaternion parent2LinkQuaternion, Vector3f axisVector,
            Vector3f parent2PivotVector, Vector3f pivot2LinkVector,
            boolean disableParentCollision);

    native private void setupSpherical(long multiBodyId, int linkIndex,
            float mass, Vector3f inertiaVector, int parentLinkIndex,
            Quaternion parent2LinkQuaternion, Vector3f parent2PivotVector,
            Vector3f pivotToLinkVector, boolean disableParentCollision);

    native private void setWorldToBaseRot(long multiBodyId,
            Quaternion quaternion);

    native private void useGlobalVelocities(long multiBodyId, boolean use);

    native private void useRK4Integration(long multiBodyId, boolean use);
}