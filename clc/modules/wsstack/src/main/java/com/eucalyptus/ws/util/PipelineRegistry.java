package com.eucalyptus.ws.util;

import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.log4j.Logger;
import org.jboss.netty.handler.codec.http.HttpRequest;

import com.eucalyptus.ws.server.DuplicatePipelineException;
import com.eucalyptus.ws.server.FilteredPipeline;
import com.eucalyptus.ws.server.NoAcceptingPipelineException;

public class PipelineRegistry {
  private static PipelineRegistry registry;
  private static Logger           LOG = Logger.getLogger( PipelineRegistry.class );

  public static PipelineRegistry getInstance( ) {
    synchronized ( PipelineRegistry.class ) {
      if ( PipelineRegistry.registry == null ) {
        PipelineRegistry.registry = new PipelineRegistry( );
      }
    }
    return PipelineRegistry.registry;
  }

  private final NavigableSet<FilteredPipeline> pipelines = new ConcurrentSkipListSet<FilteredPipeline>( );

  public void register( final FilteredPipeline pipeline ) {
    LOG.info( "Registering pipeline: " + pipeline.getPipelineName( ) );
    this.pipelines.add( pipeline );
  }

  public FilteredPipeline find( final HttpRequest request ) throws DuplicatePipelineException, NoAcceptingPipelineException {
    FilteredPipeline candidate = null;
    for ( FilteredPipeline f : this.pipelines ) {
      if ( f.accepts( request ) ) {
        if ( candidate != null ) {
          LOG.warn( "More than one candidate pipeline.  Ignoring offer by: " + f.getClass( ).getSimpleName( ) );
        } else {
          candidate = f;
        }
      }
    }
    if ( candidate == null ) { throw new NoAcceptingPipelineException( ); }
    return candidate;
  }

}
