package de.codesourcery.inversek;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJoint;
import com.badlogic.gdx.physics.box2d.joints.PrismaticJointDef;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.physics.box2d.joints.WeldJointDef;

public class WorldModel implements ITickListener , IMathSupport
{
	private final World world;
	private float accumulator = 0;

	private final List<Ball> balls = new ArrayList<>(); 
	
	private final List<IContactCallback> contactCallbacks = new ArrayList<>();

	protected static enum ItemType
	{
		GROUND( (short) 1),
		ROBOT_BASE((short) 2), 
		BONE( (short) 4),
		BALL( (short) 8);

		public final short bitMask;

		private ItemType(short bitMask) {
			this.bitMask = bitMask;
		}
	}

	public static final class Ball 
	{
		private Body body;
		public final float radius;

		public Ball(float radius) {
			this.radius = radius;
		}
		
		public Body getBody() {
			return body;
		}
		
		public void setBody(Body body) {
			this.body = body;
		}

		public Vector2 getPosition() {
			return body.getPosition();
		}
		
		@Override
		public String toString() {
			return "Ball";
		}
	}

	public WorldModel() 
	{
		world  = new World( new Vector2(0,-9.81f) , true );
		world.setContactListener( new ContactListener() 
		{
			
			@Override
			public void preSolve(Contact contact, Manifold oldManifold) {
			}
			
			@Override
			public void postSolve(Contact contact, ContactImpulse impulse) {
			}
			
			@Override
			public void endContact(Contact contact) 
			{
				for (Iterator<IContactCallback> it = contactCallbacks.iterator(); it.hasNext();) 
				{
					final IContactCallback cb = it.next();
					if ( ! cb.endContact( contact ) ) {
						it.remove();
					}
				}
			}
			
			@Override
			public void beginContact(Contact contact) 
			{
				for (Iterator<IContactCallback> it = contactCallbacks.iterator(); it.hasNext();) 
				{
					final IContactCallback cb = it.next();
					if ( ! cb.beginContact( contact ) ) {
						it.remove();
					}
				}				
			}
		});
		
		setupFloorPlane();
	}
	
	public void addContactCallback(IContactCallback cb) {
		if (cb == null) {
			throw new IllegalArgumentException("callback must not be NULL");
		}
		System.out.println("ADD CONTACT CB: "+cb);
		contactCallbacks.add( cb );
	}
	
	public void removeContractCallback(IContactCallback cb) 
	{
		if (cb == null) {
			throw new IllegalArgumentException("callback must not be NULL");
		}
		System.out.println("REMOVE CONTACT CB: "+cb);
		contactCallbacks.remove( cb );
	}

	public List<Ball> getBalls() {
		return balls;
	}

	private void setupFloorPlane() 
	{
		final Vector2 position = new Vector2(0, -Constants.FLOOR_THICKNESS/2f);
		newStaticBody( ItemType.GROUND , position )
		.boxShape(100 , Constants.FLOOR_THICKNESS)
		.collidesWith(ItemType.BALL)
		.build(null);
	}

	public void addBall(float x,float y) 
	{
		System.out.println("Adding ball @ "+x+","+y);

		// Create our body in the world using our body definition
		Ball ball = new Ball(Constants.BALL_RADIUS );
		final Body body = newDynamicBody(ItemType.BALL,new Vector2(x,y))
				.circleShape( Constants.BALL_RADIUS )
				.collidesWith( ItemType.GROUND  , ItemType.BONE , ItemType.BALL , ItemType.ROBOT_BASE )
				.restitution( 0.3f )
				.friction(1)
				.build(ball);
		ball.setBody( body );
		balls.add( ball );
	}
	
	public void destroyBall(Ball ball) 
	{
		System.out.println("Destroyed ball "+ball);
		balls.remove( ball );
		world.destroyBody( ball.body );
	}

	@Override
	public boolean tick(float deltaSeconds) 
	{
		// fixed time step
		// max frame time to avoid spiral of death (on slow devices)
		float frameTime = Math.min(deltaSeconds, 0.25f);
		accumulator += frameTime;
		while (accumulator >= Constants.PHYSICS_TIMESTEP)
		{
			world.step(Constants.PHYSICS_TIMESTEP, Constants.VELOCITY_ITERATIONS , Constants.POSITION_ITERATIONS );
			accumulator -= Constants.PHYSICS_TIMESTEP;
		}
		return true;
	}

	private List<Bone> sortLeftToRight(List<Bone> bones) 
	{
		final List<Bone> copy = new ArrayList<>(bones);

		final List<Bone> roots = copy.stream().filter( b -> b.jointA.predecessor == null ).collect( Collectors.toList() );
		if ( roots.size() != 1 ) {
			throw new IllegalArgumentException("Expected 1 root bone, found "+roots.size());
		}

		Bone previous = roots.get(0);
		copy.remove( previous );

		final List<Bone> sorted = new ArrayList<>();		
		sorted.add( previous );
		outer:		
			while ( ! copy.isEmpty() ) 
			{
				for (Iterator<Bone> it = copy.iterator(); it.hasNext();) 
				{
					Bone current = it.next();
					if ( current.jointA == previous.jointB ) 
					{
						sorted.add( current );
						it.remove();
						previous = current;
						continue outer;
					}
					throw new RuntimeException("Found no successor for "+previous);
				}
			}
		if ( sorted.size() != bones.size() ) {
			throw new RuntimeException("Something went wrong");
		}
		return sorted;
	}

	public void add(RobotArm arm) 
	{
		// create robot base
		final Body robotBase = createRobotBase();
		arm.setBase( robotBase );

		// create bones first because the joints will
		// link the bones
		final KinematicsChain chain=arm.getModel().getChains().get(0);
		final List<Bone> sortedBones = sortLeftToRight( chain.getBones() );

		final float y = Constants.ROBOTBASE_HEIGHT;
		float x1 =  de.codesourcery.inversek.Constants.JOINT_RADIUS;
		for ( Bone b : sortedBones ) 
		{
			final Vector2 boneCenter = new Vector2( x1 + b.length/2 ,y);
			if ( b instanceof Gripper ) {
				createHorizontalGripper( boneCenter , (Gripper) b );
			} else {
				createHorizontalBone( boneCenter , b );
			}
			x1 += b.length + 2 * de.codesourcery.inversek.Constants.JOINT_RADIUS;
		}

		// create joints
		final Vector2 center = new Vector2(0 , Constants.ROBOTBASE_HEIGHT ); 		
		for ( Bone b : sortedBones ) 
		{
			final de.codesourcery.inversek.Joint joint = b.jointA;
			joint.setOrientation(0);
			joint.position.set( center );
			System.out.println("Joint @ "+center);

			final Body left = joint.predecessor == null ? robotBase: joint.predecessor.getBody();
			final Body right = joint.successor.getBody();
			createJoint(joint,left,right);
			center.add( 2*de.codesourcery.inversek.Constants.JOINT_RADIUS+joint.successor.length , 0 );
		}

		chain.syncWithBox2d();
	}	

	public void createJoint(de.codesourcery.inversek.Joint joint,Body predecessor,Body successor) 
	{
		final RevoluteJointDef def = new RevoluteJointDef();
		def.collideConnected=false;
		def.bodyA = predecessor;
		if ( joint.predecessor == null ) { // attached to base
			def.localAnchorA.set( 0 , Constants.ROBOTBASE_HEIGHT/2f + Constants.JOINT_RADIUS);
		} else {
			def.localAnchorA.set( joint.predecessor.length/2+Constants.JOINT_RADIUS , 0 );
		}
		def.bodyB = successor;
		def.localAnchorB.set( -joint.successor.length/2 - Constants.JOINT_RADIUS , 0 );

		float lowerAngleLimitInDeg;
		float upperAngleLimitInDeg;
		if ( joint.range.getMinimumAngle() == 0 && joint.range.getMaximumAngle() == 360 ) {
			lowerAngleLimitInDeg = degToRad(0);
			upperAngleLimitInDeg = degToRad(360);  			
		} else {
			lowerAngleLimitInDeg = degToRad(-270);  
			upperAngleLimitInDeg = degToRad(90);  
		}
		
		def.motorSpeed=0;
		def.enableMotor = true;
		def.maxMotorTorque = Constants.MAX_MOTOR_TORQUE;
		def.enableLimit = false;		
//		def.lowerAngle = degToRad( lowerAngleLimitInDeg );
//		def.upperAngle = degToRad( upperAngleLimitInDeg);
		def.referenceAngle = 0;
		
		System.out.println("Created joint "+joint+" with limits "+
				lowerAngleLimitInDeg+"° ("+def.lowerAngle+" rad) -> "+
				upperAngleLimitInDeg+"° ("+def.upperAngle+" rad)");

		final RevoluteJoint j = (RevoluteJoint) world.createJoint(def);
		joint.setBody( j );
	}

	private Body createRobotBase() 
	{
		final Vector2 center = new Vector2(0,Constants.ROBOTBASE_HEIGHT/2);
		return newStaticBody( ItemType.ROBOT_BASE, center )
				.boxShape( Constants.ROBOTBASE_WIDTH , Constants.ROBOTBASE_HEIGHT ) 
				.collidesWith(ItemType.BALL)
				.build(null);
	}

	private Body createHorizontalBone(Vector2 center,Bone bone) 
	{
		final Body body = newDynamicBody( ItemType.BONE, center)
				.boxShape( bone.length, Constants.BONE_THICKNESS ) 
				.collidesWith( ItemType.BALL )
				.gravityScale(0)
				.build(bone);
		bone.setBody( body );
		return body;
	}	
	
	private void createHorizontalGripper(Vector2 center,Gripper gripper) 
	{
		/* Gripper looks like this:
		 * 
		 *       BP222222222
		 *       B
		 * XXXXXXD
		 *       B
		 *       BP111111111
		 *       
		 * where 
		 * 
		 * X = bone the gripper is attached to
		 * B = Gripper base plate
		 * D = Distance joint with distance 0
		 * P = Prismatic joint
		 * 1 = lower part of claw
		 * 2 = upper part of claw
		 */
		
		// create bone the gripper is attached to
		final Body gripperBase = createHorizontalBone( center , gripper );		
		
		// create base plate
		final Vector2 basePlateCenter = new Vector2( center.x + gripper.length/2 + Constants.BASEPLATE_THICKNESS/2f , center.y );
		final BoxBuilder basePlateBuilder = newDynamicBody( ItemType.BONE , basePlateCenter );
		basePlateBuilder.boxShape( Constants.BASEPLATE_THICKNESS , gripper.getMaxBaseplateLength() );
		basePlateBuilder.gravityScale(0);
		
		final Body basePlate = basePlateBuilder.collidesWith(ItemType.BALL).build(gripper);
		
		// create lower part of claw
		final Vector2 lowerClawCenter = new Vector2( basePlateCenter.x + Constants.BASEPLATE_THICKNESS/2f + gripper.getClawLength()/2f,
				basePlateCenter.y - gripper.getMaxBaseplateLength()/2 + Constants.CLAW_THICKNESS/2f );
		
		final BoxBuilder lowerClawBuilder = newDynamicBody( ItemType.BONE , lowerClawCenter )
				.boxShape( gripper.getClawLength() , Constants.CLAW_THICKNESS )
				.gravityScale( 0 )
				.friction(1)
				.collidesWith( ItemType.BALL );
		final Body lowerClaw = lowerClawBuilder.build(gripper);
				
		// create upper part of claw
		final Vector2 upperClawCenter = new Vector2( basePlateCenter.x + Constants.BASEPLATE_THICKNESS/2f + gripper.getClawLength()/2f,
				basePlateCenter.y + gripper.getMaxBaseplateLength()/2 - Constants.CLAW_THICKNESS/2f );
		final BoxBuilder upperClawBuilder = newDynamicBody( ItemType.BONE , upperClawCenter )
				.boxShape( gripper.getClawLength() , Constants.CLAW_THICKNESS )
				.gravityScale( 0 )
				.friction(1)
				.collidesWith( ItemType.BALL );
		final Body upperClaw = upperClawBuilder.build(gripper);		
		
		// create distance joint connecting the base plate with the gripper bone
		final WeldJointDef distJointDef = new WeldJointDef();
		distJointDef.collideConnected=false;
		distJointDef.bodyA = gripperBase;
		distJointDef.localAnchorA.set( gripper.length/2, 0 );
		distJointDef.bodyB = basePlate; 
		distJointDef.localAnchorB.set( -Constants.BASEPLATE_THICKNESS/2f , 0 );
		// distJointDef.length = 0;
		world.createJoint( distJointDef );
		
		// create prismatic joint connecting base plate and lower part of claw
		final PrismaticJointDef lowerJointDef = new PrismaticJointDef();
		lowerJointDef.collideConnected=false;
		lowerJointDef.bodyA = basePlate;
		lowerJointDef.localAnchorA.set( Constants.BASEPLATE_THICKNESS/2 , -gripper.getMaxBaseplateLength()/2f + Constants.CLAW_THICKNESS/2f );
		lowerJointDef.bodyB = lowerClaw; 
		lowerJointDef.localAnchorB.set( -gripper.getClawLength()/2f , 0 ); 
		lowerJointDef.referenceAngle = 0;
		lowerJointDef.maxMotorForce=10000;
		lowerJointDef.motorSpeed=0;
		lowerJointDef.enableMotor=true;
		lowerJointDef.localAxisA.set(0,1); 
		lowerJointDef.enableLimit = true;
		lowerJointDef.lowerTranslation=0;
		lowerJointDef.upperTranslation=gripper.getMaxBox2dJointTranslation();
		
		final PrismaticJoint lowerClawJoint = (PrismaticJoint) world.createJoint(lowerJointDef);
		
		lowerClawJoint.enableMotor(true);
		lowerClawJoint.setMaxMotorForce( 10000 );
		lowerClawJoint.setMotorSpeed(0);
		
		// create prismatic joint connecting base plate and upper part of claw
		final PrismaticJointDef upperJointDef = new PrismaticJointDef();
		upperJointDef.collideConnected=false;
		upperJointDef.bodyA = basePlate;
		upperJointDef.localAnchorA.set( Constants.BASEPLATE_THICKNESS/2 , gripper.getMaxBaseplateLength()/2f - Constants.CLAW_THICKNESS/2f );
		upperJointDef.bodyB = upperClaw; 
		upperJointDef.localAnchorB.set( -gripper.getClawLength()/2f , 0  ); 
		upperJointDef.referenceAngle = 0;
		upperJointDef.localAxisA.set(0,-1);
		lowerJointDef.maxMotorForce=10000;
		lowerJointDef.motorSpeed=0;
		lowerJointDef.enableMotor=true;		
		upperJointDef.enableLimit = true;
		upperJointDef.lowerTranslation=0; 
		upperJointDef.upperTranslation= gripper.getMaxBox2dJointTranslation();
		
		final PrismaticJoint upperClawJoint = (PrismaticJoint) world.createJoint(upperJointDef);
		
		upperClawJoint.enableMotor(true);
		upperClawJoint.setMaxMotorForce( 10000 );
		upperClawJoint.setMotorSpeed(0);
		
		gripper.setOpenPercentage( 1.0f ); // 100% open
		
		gripper.setBasePlateBody( basePlate );
		gripper.setLowerClawBody( lowerClaw );
		gripper.setUpperClawBody( upperClaw );
		gripper.setLowerJoint( lowerClawJoint );
		gripper.setUpperJoint( upperClawJoint );
	}
	
	private static float convertAngle( float angleInDeg ) {
		if ( angleInDeg > 180 ) {
			return angleInDeg - 360;
		}
		return angleInDeg;
	}

	private BoxBuilder newDynamicBody(ItemType type,Vector2 center) 
	{
		return newBody(type,center,BodyType.DynamicBody);
	}
	
	private BoxBuilder newKinematicBody(ItemType type,Vector2 center) 
	{
		return newBody(type,center,BodyType.KinematicBody);
	}	

	private BoxBuilder newStaticBody(ItemType type,Vector2 center) 
	{	
		return newBody(type,center,BodyType.StaticBody);
	}

	private BoxBuilder newBody(ItemType type,Vector2 center,BodyType bodyType) 
	{
		return new BoxBuilder(type,center,bodyType);
	}

	protected final class BoxBuilder 
	{
		private final Vector2 center;
		private final ItemType itemType;
		private final Set<ItemType> collidesWith = new HashSet<>();
		private final BodyType bodyType;
		private Shape shape;
		private boolean isBuilt;
		private float restitution=0;
		private float friction;
		private float gravityScale=1f;

		public BoxBuilder(ItemType type,Vector2 center, BodyType bodyType) 
		{
			this.itemType = type;
			this.center = center.cpy();
			this.bodyType = bodyType;
		}
		
		public BoxBuilder gravityScale(float gravityScale) {
			this.gravityScale = gravityScale;
			return this;
		}

		public BoxBuilder restitution(float value) {
			this.restitution = value;
			return this;
		}

		public BoxBuilder friction(float value) {
			this.friction = value;
			return this;
		}		

		public BoxBuilder collidesWith(ItemType t1,ItemType... other) 
		{
			assertNotBuilt();

			if ( t1 == null ) {
				throw new IllegalArgumentException("t1 must not be NULL");
			}
			collidesWith.add( t1 );
			if ( other != null ) {
				collidesWith.addAll( Arrays.asList( other ) );
			}
			return this;
		}

		private void assertNotBuilt() {
			if ( isBuilt ) {
				throw new IllegalStateException("Already built!");
			}
		}

		public BoxBuilder circleShape(float radius) {
			assertNotBuilt();
			final CircleShape circle = new CircleShape();
			circle.setRadius( radius );
			this.shape = circle;
			return this;
		}

		public BoxBuilder boxShape(float width, float height) {
			assertNotBuilt();

			if ( this.shape != null ) {
				throw new IllegalStateException("Shape already set to "+this.shape);
			}
			final PolygonShape box = new PolygonShape();
			box.setAsBox( width/2f , height/2f );
			this.shape = box;
			return this;
		}

		public Body build(Object fixtureUserData) 
		{
			assertNotBuilt();

			if ( shape == null ) {
				throw new IllegalStateException("Shape not set?");
			}

			System.out.println("Creating "+bodyType+" body @ "+center+" with shape "+this.shape);

			final BodyDef bodyDef = new BodyDef();
			bodyDef.type = bodyType;
			bodyDef.position.set( center );
			bodyDef.gravityScale = this.gravityScale;

			final Body body = world.createBody(bodyDef);

			FixtureDef fixtureDef = new FixtureDef();
			fixtureDef.shape = this.shape;
			fixtureDef.density = Constants.DENSITY;
			fixtureDef.friction = this.friction;
			fixtureDef.restitution = this.restitution; 
			fixtureDef.filter.categoryBits = this.itemType.bitMask;
			fixtureDef.filter.maskBits = (short) collidesWith.stream().mapToInt( m -> m.bitMask ).sum();

			final Fixture fixture = body.createFixture(fixtureDef);
			fixture.setUserData( fixtureUserData );
			
			this.shape.dispose();
			this.shape = null;
			return body;			
		}
	}
}