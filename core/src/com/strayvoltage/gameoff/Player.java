package com.strayvoltage.gameoff;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import java.util.Iterator;
import java.util.ArrayList;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.strayvoltage.gamelib.*;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.BodyDef.*;
import com.badlogic.gdx.utils.Array;

public class Player extends GameSprite {

  GameInputManager2 m_controller;
  float m_dx = 0;
  float m_dy = 0;
  GameTileMap m_map = null;
  boolean m_powered = false;
  boolean m_playerControlled = false;
  Player m_otherPlayer = null;
  int m_controlDelayTicks = 5;
  float m_jumpDY = 10;
  float m_gravity = -0.2f;
  int m_jumpTicks = 0;
  boolean m_onGround = true;
  PowerUnit m_powerUnit = null;
  float m_lastDx = 1;
  boolean m_ownsPowerUnit = false;
  int m_firePressedTicks = 0;
  int m_throwDelayTicks = 60;
  Fixture m_fixture = null;
  Fixture playerSensorFixture = null;
  int m_categoryBits = 0;
  int m_tempTicks = 0;
  long lastGroundTime = 0;
  float stillTime = 0;
  World m_world = null;
  float m_hForce = 50;

  public Player(TextureRegion texture, GameInputManager2 controller)
  {
    super(texture);
    m_controller = controller;
  }

  public void setMap(GameTileMap m, Player p, float jumpDY, PowerUnit pu)
  {
    m_map = m;
    m_otherPlayer = p;
    m_jumpDY = jumpDY;
    m_powerUnit = pu;
  }

  public void addToWorld(World world)
  {
    m_world = world;
    BodyDef bodyDef = new BodyDef();
    bodyDef.type = BodyType.DynamicBody;
    bodyDef.fixedRotation = true;
    bodyDef.bullet = true;

    m_body = world.createBody(bodyDef);
    m_body.setUserData(this);

    FixtureDef fixtureDef = new FixtureDef();

    PolygonShape rect = null;
    rect = new PolygonShape();
    rect.setAsBox(this.getWidth()/(2*Box2dVars.PIXELS_PER_METER), this.getHeight()/(2*Box2dVars.PIXELS_PER_METER));
    fixtureDef.shape = rect;

    fixtureDef.density = 1.0f; 
    fixtureDef.friction = 0.5f;
    fixtureDef.restitution = 0.2f;

    fixtureDef.filter.categoryBits = Box2dVars.PLAYER_NORMAL;
    fixtureDef.filter.maskBits = Box2dVars.FLOOR | Box2dVars.BLOCK | Box2dVars.PLATFORM;

    m_fixture = m_body.createFixture(fixtureDef);
    rect.dispose();

    fixtureDef = new FixtureDef();
    CircleShape circle = new CircleShape();		
		circle.setRadius((this.getWidth()-2)/(2*Box2dVars.PIXELS_PER_METER));
		circle.setPosition(new Vector2(0, -this.getHeight()/(2*Box2dVars.PIXELS_PER_METER)));
    fixtureDef.shape = circle;

    fixtureDef.density = 0f; 
    fixtureDef.friction = 0f;
    fixtureDef.restitution = 0f;
    fixtureDef.filter.categoryBits = Box2dVars.PLAYER_NORMAL;
    fixtureDef.filter.maskBits = Box2dVars.FLOOR | Box2dVars.BLOCK | Box2dVars.PLATFORM;

		playerSensorFixture = m_body.createFixture(fixtureDef);
    playerSensorFixture.setSensor(true);		
		circle.dispose();		
		
  }

  public GameMapObject checkObjectCollisions(float xx, float yy)
  {
    float oldx = this.getX();
    float oldy = this.getY();
    this.setPosition(xx,yy);
    MainLayer m = (MainLayer) getParent();
    for (GameMapObject o : m.m_gameMapObjects)
    {
      if ((o.colBits() & 3) > 0)
      {
        if (Intersector.overlaps(this.getBoundingRectangle(), o.getBoundingRectangle()))
        {
          this.setPosition(oldx,oldy);
          return o;
        }
      }
    }
    this.setPosition(oldx,oldy);
    return null;
  }

  public float dist(float xx, float yy)
  {
    float px = this.getX() + this.getWidth()/2;
    float py = this.getY() + this.getHeight()/2;
    return (float)Math.sqrt(Math.pow((double)(xx- px), 2) + Math.pow((double)(yy-py), 2));
  }

  private boolean isPlayerGrounded(float deltaTime) {

		//groundedPlatform = null;
		Array<Contact> contactList = m_world.getContactList();
		for(int i = 0; i < contactList.size; i++) {
			Contact contact = contactList.get(i);
			if(contact.isTouching() && (contact.getFixtureA() == playerSensorFixture ||
			   contact.getFixtureB() == playerSensorFixture)) {				
				Vector2 pos = m_body.getPosition();
				WorldManifold manifold = contact.getWorldManifold();
				boolean below = true;
				for(int j = 0; j < manifold.getNumberOfContactPoints(); j++) {
					below = below && (manifold.getPoints()[j].y < (pos.y - this.getHeight()/(2*Box2dVars.PIXELS_PER_METER) - 0.1f));
				}
				
				if(below) {
          /*
					if(contact.getFixtureA().getUserData() != null && contact.getFixtureA().getUserData().equals("p")) {
						groundedPlatform = (MovingPlatform)contact.getFixtureA().getBody().getUserData();							
					}
					
					if(contact.getFixtureB().getUserData() != null && contact.getFixtureB().getUserData().equals("p")) {
						groundedPlatform = (MovingPlatform)contact.getFixtureB().getBody().getUserData();
					}	
          */						
			
					return true;			
				}

				return false;
			}
		}

		return false;
	}

  public void update(float deltaTime)
  {
    if (m_playerControlled)
    {
      if (m_ownsPowerUnit == false)
      {
        m_powered = false;
        if (m_powerUnit.canPickUp())
        {
          if ((dist(m_powerUnit.getX(), m_powerUnit.getY())) < 100)
          {
            m_powered = true;
          }
        }
      }
    }

    if ((m_powered) && (m_playerControlled))
    {
      Vector2 cv = m_body.getLinearVelocity();
      boolean notMoved = true;

      m_onGround = isPlayerGrounded(Gdx.graphics.getDeltaTime());
      if(m_onGround) {
        lastGroundTime = System.nanoTime();
      } else {
        if(System.nanoTime() - lastGroundTime > 100000000) {
          //m_onGround = true;
        }
      }

      if (m_controller.isRightPressed())
      {
        m_body.applyForceToCenter(m_hForce,0,true);
        m_lastDx = 1;
        notMoved = false;
        stillTime = 0;
      } else if (m_controller.isLeftPressed())
      {
        m_body.applyForceToCenter(-m_hForce,0,true);
        m_lastDx = -1;
        notMoved = false;
        stillTime = 0;
      } else {		
			    stillTime += Gdx.graphics.getDeltaTime();
          m_body.setLinearVelocity(cv.x * 0.9f, cv.y);
      }

      
      if (Math.abs(cv.x) > 4)
      {
        if (cv.x > 0) cv.x = 4;
        else cv.x = -4;
        m_body.setLinearVelocity(cv);
      }

      if(!m_onGround) {			
        m_fixture.setFriction(0f);
        playerSensorFixture.setFriction(0f);			
      } else {


        if(notMoved && stillTime > 0.2) {
          m_fixture.setFriction(100f);
          playerSensorFixture.setFriction(100f);
        }
        else {
          m_fixture.setFriction(0.2f);
          playerSensorFixture.setFriction(0.2f);
        }
        
        //if(groundedPlatform != null && groundedPlatform.dist == 0) {
        //  player.applyLinearImpulse(0, -24, pos.x, pos.y);				
        //}
      }	

      if (m_controller.isFirePressed())
      {
        if (m_ownsPowerUnit)
        {
          m_firePressedTicks++;
        }
      } else
      {
        if (m_firePressedTicks > 0)
        {
          throwUnit();
          m_firePressedTicks = 0;
        }
      }
    }

    if (m_controlDelayTicks > 0)
    {
      m_controlDelayTicks--;
    } else if (m_playerControlled)
    {
      if (m_controller.isTriggerPressed())
      {
        m_playerControlled = false;
        m_otherPlayer.playerTakeControl();
      }
    }

    //now do vertical
    if ((m_powered) && (m_playerControlled))
    {
      if (m_onGround)
      {
        if (m_controller.isJumpPressed())
        {
          if (m_jumpTicks > 0)
          {
            m_jumpTicks--;
          } else
          {
            m_body.applyLinearImpulse(0, m_jumpDY, 0, 0, true);
            m_jumpTicks = 12;
            m_onGround = false;
          }
        }
      } else
      {
        if (m_jumpTicks > 0) m_jumpTicks--;
        if ((m_jumpTicks > 0) && (m_controller.isJumpPressed()))
        {
          //m_body.applyForceToCenter(0,-1,true);
        } else
        {
          m_jumpTicks = 0;
        }
      }
    }

    this.setPositionToBody();

    if (m_throwDelayTicks > 0) m_throwDelayTicks--;
    else
    {
      if (m_powerUnit.canPickUp())
      {
        if (Intersector.overlaps(this.getBoundingRectangle(), m_powerUnit.getBoundingRectangle()))
        {
          m_powerUnit.pickUp(this);
        }
      }
    }

  }

  public void setPositionToBody()
  {
    float cx = m_body.getPosition().x*Box2dVars.PIXELS_PER_METER;
    float cy = m_body.getPosition().y*Box2dVars.PIXELS_PER_METER;
    this.setPosition(cx - this.getWidth()/2, cy - this.getHeight()/2);
  }

  public void powerOn()
  {
    m_powered = true;
  }

  public void setPowerUnit(PowerUnit pu)
  {
    m_powerUnit = pu;
    m_powered = true;
    m_ownsPowerUnit = true;
  }

  public void powerOff()
  {
    m_powered = false;
  }

  public void throwUnit()
  {
    if (m_ownsPowerUnit == false) return;
    if (m_powerUnit == null) return;
    if (m_powered == false) return;

    m_ownsPowerUnit = false;
    m_powered = false;

    if (m_firePressedTicks > 30) m_firePressedTicks = 30;

    float r = ((float)m_firePressedTicks / 30f) * 8;

    if (m_lastDx > 0)
    {
      m_powerUnit.throwUnit(r,2);
       m_throwDelayTicks = 60;
    } else
    {
      m_powerUnit.throwUnit(-r,2);
      m_throwDelayTicks = 60;
    }
  }

  public void playerTakeControl()
  {
    m_playerControlled = true;
    m_controlDelayTicks = 30;
  }

  public void setBodyPosition(float xx, float yy)
  {
    this.setPosition(xx,yy);
    m_body.setTransform((xx + this.getWidth()/2)/Box2dVars.PIXELS_PER_METER , (yy + this.getHeight()/2)/Box2dVars.PIXELS_PER_METER, this.getRotation()/180f * 3.14f);
  }

  public void setActive(boolean b)
  {
    this.setVisible(b);
  }

  public boolean isAlive()
  {
    return true;
  }
  
}
