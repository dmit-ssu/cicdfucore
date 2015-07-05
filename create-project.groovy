import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException

// Retrieve parameters of the current build
def cicd_project = build.buildVariableResolver.resolve("CICD_PROJECT_NAME")
def cicd_jobparam = build.buildVariableResolver.resolve("CICD_COMMON_NAME")
def cicd_typeparam = build.buildVariableResolver.resolve("CICD_COMMON_TYPE")
def cicd_gitlinkparam = build.buildVariableResolver.resolve("CICD_GITLINK")
def cicd_slavecustomparam = build.buildVariableResolver.resolve("CICD_SLAVECUSTOMNAME")
def cicd_gitbranchparam = build.buildVariableResolver.resolve("CICD_GITBRANCH")
def cicd_core_repo = build.buildVariableResolver.resolve("CICD_CORE_REPO")
def cicd_template_name = build.buildVariableResolver.resolve("CICD_TEMPLATE_PROJECT")
if (cicd_template_name == null) cicd_template_name = "template_project"
// Generate parameters for project job as well as template project job name
String project_name = cicd_project.toLowerCase()
String project_jobname = "project_" + project_name
String common_name = cicd_jobparam.toLowerCase()
String common_type = cicd_typeparam.toLowerCase()
String custom_slavename = cicd_slavecustomparam.toLowerCase()

// Creating project if not exists
def project_job = Hudson.instance.getJob(project_jobname)
if (project_job == null) {
   def template
   try {
      template = Hudson.instance.getItem(cicd_template_name)
   } catch (NullPointerException x) {
      println "Template job $cicd_template_name for project creation not found"
      throw new AbortException("Creating $cicd_project project from template aborted.")
   }
   project_job = Hudson.instance.copy(template, project_jobname)

   def job_prop_name = "CICD_COMMON_NAME"
   def type_prop_name = "CICD_COMMON_TYPE"
   def gitlink_prop_name = "CICD_GITLINK"
   def slavename_prop_name = "CICD_SLAVECUSTOMNAME"
   def gitbranch_prop_name = "CICD_GITBRANCH"
   def corerepo_prop_name = "CICD_CORE_REPO"

   def job_prop = null
   def type_prop = null
   def gitlink_prop = null
   def slavename_prop = null
   def gitbranch_prop = null
   def corerepo_prop = null

   def props = project_job.getProperty(ParametersDefinitionProperty.class)
   def i = props.getParameterDefinitions().iterator()
   while (i.hasNext()) {
     def job = i.next()
      if (job.name == job_prop_name) {
         job_prop = new StringParameterDefinition(job.name, common_name, job.getDescription())
         i.remove()
         continue
      }
      if (job.name == type_prop_name) {
         type_prop = new StringParameterDefinition(job.name, common_type, job.getDescription())
         i.remove()
         continue
      }
      if (job.name == gitlink_prop_name) {
         gitlink_prop = new StringParameterDefinition(job.name, cicd_gitlinkparam, job.getDescription())
         i.remove()
         continue
      }
      if (job.name == slavename_prop_name) {
         slavename_prop = new StringParameterDefinition(job.name, custom_slavename, job.getDescription())
         i.remove()
         continue
      }
      if (job.name == gitbranch_prop_name) {
         gitbranch_prop = new StringParameterDefinition(job.name, cicd_gitbranchparam, job.getDescription())
         i.remove()
         continue
      }
      if (job.name == corerepo_prop_name) {
         if (cicd_core_repo != null)
            corerepo_prop = new StringParameterDefinition(job.name, cicd_core_repo, job.getDescription())
         i.remove()
         continue
      }
   }
     println "Props4."
   if (job_prop == null)
      job_prop = new StringParameterDefinition(job_prop_name, common_name, "")
   props.getParameterDefinitions().add(job_prop)
   if (type_prop == null)
      type_prop = new StringParameterDefinition(type_prop_name, common_type, "")
   props.getParameterDefinitions().add(type_prop)
   if (gitlink_prop == null)
      gitlink_prop = new StringParameterDefinition(gitlink_prop_name, cicd_gitlinkparam, "")
   props.getParameterDefinitions().add(gitlink_prop)
   if (slavename_prop == null)
      slavename_prop = new StringParameterDefinition(slavename_prop_name, custom_slavename, "")
   props.getParameterDefinitions().add(slavename_prop)
   if (gitbranch_prop == null)
      gitbranch_prop = new StringParameterDefinition(gitbranch_prop_name, cicd_gitbranchparam, "")
   props.getParameterDefinitions().add(gitbranch_prop)
   if (corerepo_prop == null && cicd_core_repo != null)
      corerepo_prop = new StringParameterDefinition(corerepo_prop_name, cicd_core_repo, "")
   if (corerepo_prop != null) 
      props.getParameterDefinitions().add(corerepo_prop)

   project_job.save()
   Hudson.instance.reload();
   println "Job $project_jobname from template created"
} else {
   println "Project job as $project_jobname already exists"
   throw new AbortException("Creating $cicd_project aborted.")
}

return build.result
