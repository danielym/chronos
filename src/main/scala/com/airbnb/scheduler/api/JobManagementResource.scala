package com.airbnb.scheduler.api

import java.util.logging.{Level, Logger}
import javax.ws.rs._
import javax.ws.rs.core.{MediaType, Response}
import javax.ws.rs.core.Response.Status
import scala.Array
import scala.collection.mutable.ListBuffer

import com.airbnb.scheduler.config.SchedulerConfiguration
import com.airbnb.scheduler.graph.JobGraph
import com.airbnb.scheduler.jobs._
import com.google.inject.Inject
import com.yammer.metrics.annotation.Timed
import org.joda.time.{DateTimeZone, DateTime}

/**
 * The REST API for managing jobs.
 * @author Florian Leibert (flo@leibert.de)
 */
//TODO(FL): Create a case class that removes epsilon from the dependent.
@Path(PathConstants.jobBasePath)
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class JobManagementResource @Inject()(val jobScheduler: JobScheduler,
                                      val jobGraph: JobGraph,
                                      val configuration: SchedulerConfiguration) {

  private[this] val log = Logger.getLogger(getClass.getName)

  @Path(PathConstants.jobPatternPath)
  @DELETE
  @Timed
  def delete(@PathParam("jobName") jobName: String, @QueryParam("force") force: Boolean): Response = {
    try {
      require(!jobGraph.lookupVertex(jobName).isEmpty, "Job '%s' not found".format(jobName))
      require(jobGraph.getChildren(jobName).isEmpty || force,
        "The job '%s' has children, you cannot delete it without deleting it's children first".format(jobName))

      jobScheduler.deregisterJob(jobGraph.lookupVertex(jobName).get, true, force)
      Response.noContent().build
    } catch {
      case ex: IllegalArgumentException => {
        log.log(Level.INFO, "Bad Request", ex)
        return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage)
          .build()
      }
      case ex: Throwable => {
        log.log(Level.WARNING, "Exception while serving request", ex)
        return Response.serverError().build
      }
    }
  }

  @GET
  @Path(PathConstants.jobStatsPatternPath)
  def getStat(@PathParam("jobName") jobName: String) : Response = {
    try {
      require(!jobGraph.lookupVertex(jobName).isEmpty, "Job '%s' not found".format(jobName))
      val job = jobGraph.getJobForName(jobName).get
      return Response.ok(JobUtils.getJsonStats(jobName)).build()
    } catch {
      case ex: IllegalArgumentException => {
        log.log(Level.INFO, "Bad Request", ex)
        return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage)
          .build()
      }
      case ex: Throwable => {
        log.log(Level.WARNING, "Exception while serving request", ex)
        return Response.serverError().build
      }
    }
  }

  @Path(PathConstants.jobPatternPath)
  @PUT
  @Timed
  def trigger(@PathParam("jobName") jobName: String): Response = {
    try {
      require(!jobGraph.lookupVertex(jobName).isEmpty, "Job '%s' not found".format(jobName))
      val job = jobGraph.getJobForName(jobName).get
      log.info("Manually triggering job:" + jobName)
      jobScheduler.taskManager.enqueue(TaskUtils.getTaskId(job, DateTime.now(DateTimeZone.UTC), 0))
      Response.noContent().build
    } catch {
      case ex: IllegalArgumentException => {
        log.log(Level.INFO, "Bad Request", ex)
        return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage)
          .build()
      }
      case ex: Throwable => {
        log.log(Level.WARNING, "Exception while serving request", ex)
        return Response.serverError().build
      }
    }
  }

  @Path(PathConstants.allJobsPath)
  @GET
  @Timed
  def list(): Response = {
    try {
      val jobs = ListBuffer[BaseJob]()
      import scala.collection.JavaConversions._
      jobGraph.dag.vertexSet().map({
        job =>
          jobs += jobGraph.getJobForName(job).get
      })
      return Response.ok(jobs.toList).build
    } catch {
      case ex: Throwable => {
        log.log(Level.WARNING, "Exception while serving request", ex)
        throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
      }
    }
  }
}