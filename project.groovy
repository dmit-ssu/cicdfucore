import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException

// Retrieve parameters of the current build
def cicd_jobparam = build.buildVariableResolver.resolve("CICD_JOB")
def cicd_gitlinkparam = build.buildVariableResolver.resolve("CICD_GITLINK")
def cicd_slavecustomparam = build.buildVariableResolver.resolve("CICD_SLAVECUSTOMNAME")
def cicd_gitbranchparam = build.buildVariableResolver.resolve("CICD_GITBRANCH")
// Generate parameters for common build as well as common job name
String common_name = cicd_jobparam.toLowerCase()
String common_jobname = "common_" + common_name
String slavename = cicd_slavecustomparam.toLowerCase() + "_slave"
def params = [
      new StringParameterValue('CICD_GITLINK', cicd_gitlinkparam),
      new StringParameterValue('CICD_SLAVENAME', slavename),
      new StringParameterValue('CICD_GITBRANCH', cicd_gitbranchparam),
      new StringParameterValue('CICD_JOBNAME', common_jobname),
      new StringParameterValue('CICD_BUILD', build.toString().tr(" ", "_")),
   ]

//Running a clean job first
def common_job_clean = Hudson.instance.getJob("common_clean")
def clean_build
try {
   //Sharing parameters between main build and common_job_clean build
   def future = common_job_clean.scheduleBuild2(0, new Cause.UpstreamCause(build), new ParametersAction(params))
   println "Waiting for the completion of " + HyperlinkNote.encodeTo('/' + common_job_clean.url, common_job_clean.fullDisplayName) + " for " + cicd_jobparam
   clean_build = future.get()
} catch (CancellationException x) {
   throw new AbortException("${common_job.fullDisplayName} aborted.")
}
catch (NullPointerException x) {
   println "common_clean job was not found. Have you copied it?"
   throw new AbortException("Cleaning failed.")
}

build.result = clean_build.result
if (clean_build.result != Result.SUCCESS && clean_build.result != Result.UNSTABLE) {
// We abort this build right here and now.
   throw new AbortException("${clean_build.fullDisplayName} failed.")
}

//Getting actual job
def common_job = Hudson.instance.getJob(common_jobname)
if (common_job == null) {
   println "Common job as $cicd_jobparam not found... trying to create it from template."

   String template_name = "common_template"
   String common_prop_name = "CICD_COMMON_NAME"
   def template
   try {
      template = Hudson.instance.getItem(template_name)
   } catch (NullPointerException x) {
      println "Template job $template_name for common jobs not found"
      throw new AbortException("Creating $cicd_jobparam from template aborted.")
   }
   common_job = Hudson.instance.copy(template, common_jobname)

   def common_prop = null
   def props = common_job.getProperty(ParametersDefinitionProperty.class)
   def i = props.getParameterDefinitions().iterator()
   while (i.hasNext()) {
      if (i.next().name == common_prop_name) {
         common_prop = new StringParameterDefinition(i.name, common_name, i.getDescription())
         i.remove()
         break
      }
   }
   if (common_prop == null)
      common_prop = new StringParameterDefinition(common_prop_name, common_name, "")
   props.getParameterDefinitions().add(common_prop)

   common_job.save()
   Hudson.instance.reload();
   println "Job $cicd_jobparam from template created"

   common_job = Hudson.instance.getJob(common_jobname)
}

//Running actual job
def new_build
try {
   //Sharing parameters between main build and common_job build
   build.addAction(new ParametersAction(params))
   def future = common_job.scheduleBuild2(0, new Cause.UpstreamCause(build), new ParametersAction(params))
   println "Waiting for the completion of " + HyperlinkNote.encodeTo('/' + common_job.url, common_job.fullDisplayName) + " for " + cicd_jobparam
   new_build = future.get()
} catch (CancellationException x) {
   throw new AbortException("${common_job.fullDisplayName} aborted.")
}
catch (NullPointerException x) {
   println "No such common job as $cicd_jobparam"
   throw new AbortException("$cicd_jobparam aborted.")
}

println HyperlinkNote.encodeTo('/' + new_build.url, new_build.fullDisplayName) + " for " + cicd_jobparam + " completed. Result was " + new_build.result

// Check that it succeeded
build.result = new_build.result
if (new_build.result != Result.SUCCESS && new_build.result != Result.UNSTABLE) {
// We abort this build right here and now.
   throw new AbortException("${new_build.fullDisplayName} failed.")
}

return build.result
