/*******************************************************************************
 *Copyright (c) 2009  Eucalyptus Systems, Inc.
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, only version 3 of the License.
 * 
 * 
 *  This file is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 * 
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Please contact Eucalyptus Systems, Inc., 130 Castilian
 *  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 *  if you need additional information or have any questions.
 * 
 *  This file may incorporate work covered under the following copyright and
 *  permission notice:
 * 
 *    Software License Agreement (BSD License)
 * 
 *    Copyright (c) 2008, Regents of the University of California
 *    All rights reserved.
 * 
 *    Redistribution and use of this software in source and binary forms, with
 *    or without modification, are permitted provided that the following
 *    conditions are met:
 * 
 *      Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 * 
 *      Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 * 
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 *    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 *    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 *    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 *    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 *    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 *    THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 *    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 *    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 *    ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************
 * @author chris grzegorczyk <grze@eucalyptus.com>
 */
package com.eucalyptus.bootstrap;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.apache.log4j.Logger;
import com.eucalyptus.records.EventRecord;
import com.eucalyptus.records.EventType;
import com.eucalyptus.system.Ats;
import com.google.common.collect.Lists;

public class BootstrapperDiscovery extends ServiceJarDiscovery {
  private static Logger LOG = Logger.getLogger( BootstrapperDiscovery.class );
  private static List<Class>         bootstrappers = Lists.newArrayList( );

  public BootstrapperDiscovery() {}
  
  /**
   * Find 
   * @see com.eucalyptus.bootstrap.ServiceJarDiscovery#processClass(java.lang.Class)
   * @param candidate
   * @return
   * @throws Exception
   */
  @Override
  public boolean processClass( Class candidate ) throws Exception {
    String bc = candidate.getCanonicalName( );
    Class bootstrapper = this.getBootstrapper( candidate );
    if ( !Ats.from( candidate ).has( RunDuring.class ) ) {
      throw BootstrapException.throwFatal( "Bootstrap class does not specify execution stage (RunDuring.value=Bootstrap.Stage): " + bc );
    } else if ( !Ats.from( candidate ).has( Provides.class ) ) {
      throw BootstrapException.throwFatal( "Bootstrap class does not specify provided component (Provides.value=Component): " + bc );
    }
    this.bootstrappers.add( bootstrapper );
    return true;
  }

  @SuppressWarnings( "unchecked" )
  public static List<Bootstrapper> getBootstrappers( ) {
    List<Bootstrapper> ret = Lists.newArrayList( );
    for ( Class c : bootstrappers ) {
      if ( c.equals( SystemBootstrapper.class ) ) continue;
      try {
        EventRecord.here( BootstrapperDiscovery.class, EventType.BOOTSTRAPPER_INIT,"<init>()V", c.getCanonicalName( ) ).info( );
        try {
          ret.add( ( Bootstrapper ) c.newInstance( ) );
        } catch ( Exception e ) {
          EventRecord.here( BootstrapperDiscovery.class, EventType.BOOTSTRAPPER_INIT,"getInstance()L", c.getCanonicalName( ) ).info( );
          try {
            Method m = c.getDeclaredMethod( "getInstance", new Class[] {} );
            ret.add( ( Bootstrapper ) m.invoke( null, new Object[] {} ) );
          } catch ( NoSuchMethodException ex ) {
            throw BootstrapException.throwFatal( "Error in <init>()V in bootstrapper: " + c.getCanonicalName( ), e );
          }
        }
      } catch ( Exception e ) {
        throw BootstrapException.throwFatal( "Error in <init>()V and getInstance()L; in bootstrapper: " + c.getCanonicalName( ), e );
      }
    }
    return ret;
  }

  @SuppressWarnings( "unchecked" )
  private Class getBootstrapper( Class candidate ) throws Exception {
    if ( Modifier.isAbstract( candidate.getModifiers( ) ) ) throw new InstantiationException( candidate.getName( ) + " is abstract." );
    if ( !Bootstrapper.class.isAssignableFrom( candidate ) ) throw new InstantiationException( candidate + " does not conform to " + Bootstrapper.class );
    LOG.trace( "Candidate bootstrapper: " + candidate.getName( ) );
    if ( !Modifier.isPublic( candidate.getDeclaredConstructor( new Class[] {} ).getModifiers( ) ) ) {
      Method factory = candidate.getDeclaredMethod( "getInstance", new Class[] {} );
      if ( !Modifier.isStatic( factory.getModifiers( ) ) || !Modifier.isPublic( factory.getModifiers( ) ) ) {
        throw new InstantiationException( candidate.getCanonicalName( ) + " does not declare public <init>()V or public static getInstance()L;" );
      }
    }
    EventRecord.here( ServiceJarDiscovery.class, EventType.BOOTSTRAPPER_ADDED, candidate.getName() ).info( );
    return candidate;
  }

  @Override
  public Double getPriority( ) {
    return 0.0d;
  }
  
}
