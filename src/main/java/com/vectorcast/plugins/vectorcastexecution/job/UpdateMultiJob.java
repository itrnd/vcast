/*
 * The MIT License
 *
 * Copyright 2016 Vector Software, East Greenwich, Rhode Island USA
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.vectorcast.plugins.vectorcastexecution.job;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import com.tikal.jenkins.plugins.multijob.MultiJobBuilder;
import com.tikal.jenkins.plugins.multijob.PhaseJobsConfig;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Project;
import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.CopyArtifact;
import hudson.plugins.copyartifact.WorkspaceSelector;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import com.vectorcast.plugins.vectorcastexecution.VectorCASTCommand;

/**
 * Update a multi-job project.
 * Basically
 *     re-create top-level multi-job (delete then create)
 *     delete any projects no longer needed.
 *     add any new projects now required
 */
public class UpdateMultiJob extends NewMultiJob {
    /** Deleted jobs */
    private List<String> deleted = null;
    private String jobFullName;

    public void setJobFullName(String name) {
        this.jobFullName = name;
    }
    /**
     * Get the list of deleted jobs
     * @return the deleted jobs
     */
    public List<String> getDeleted() {
        return deleted;
    }
    /**
     * Constructor
     * @param request request object
     * @param response response object
     * @param useSavedData use saved data true/false
     * @throws ServletException exception
     * @throws IOException exception
     */
    public UpdateMultiJob(final StaplerRequest request, final StaplerResponse response, boolean useSavedData) throws ServletException, IOException {
        super(request, response, useSavedData);
        this.jobFullName = getBaseName() + ".vcast.updatemulti";
    }
    /**
     * Get the multi job name
     * @return multi-job name
     */
    public String getMultiJobName() {
        String projectName = getBaseName() + ".vcast.multi";
        return projectName;
    }
    /**
     * Do update
     * @throws IOException exception
     * @throws ServletException exception
     * @throws hudson.model.Descriptor.FormException exception
     * @throws InterruptedException exception
     * @throws JobAlreadyExistsException exception
     * @throws InvalidProjectFileException exception
     */
    public void update() throws IOException, ServletException, Descriptor.FormException, InterruptedException, JobAlreadyExistsException, InvalidProjectFileException {

        // Read the manage project file
        FileItem fileItem = getRequest().getFileItem("manageProject");
        if (fileItem == null) {
            return;
        }

        manageFile = fileItem.getString();
        manageProject = new ManageProject(manageFile);
        manageProject.parse();
        Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX manageProject.getJobs() = " + manageProject.getJobs());

        String folderName = FilenameUtils.getFullPath(jobFullName);
        String projectName = folderName + getMultiJobName();
        MultiJobProject project = (MultiJobProject)getInstance().getItemByFullName(projectName);

        projectsAdded = new ArrayList<>();
        projectsExisting = new ArrayList<>();

        // Collect the list of existing phase jobs within the vcast multi project
        MultiJobBuilder multiJobBuilder = null;
        VectorCASTCommand reportsVectorCASTCmd = null; /* The reports VectorCASTCommand builder should be the
                                                       last one in the builders list. Get a handle on it now in
                                                       order to re-add it to the bottom of the list later. */
        for (Builder builder : project.getBuilders()) {
            if (builder instanceof MultiJobBuilder) {
                // This branch should be reached at least once.
                // A VCast Multi job should have at least a (empty - no phase jobs) MultiJobBuilder.
                multiJobBuilder = (MultiJobBuilder) builder;
                Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX Found multijob builder = " + multiJobBuilder.getPhaseName());
                //break;
            }
            else if (builder instanceof VectorCASTCommand) {
                reportsVectorCASTCmd = (VectorCASTCommand) builder;
                Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX VectorCASTCommand = " + reportsVectorCASTCmd.getUnixCommand());
            }
        }

        List<PhaseJobsConfig> existingPhaseJobs = new ArrayList<>();
        try {
            existingPhaseJobs.addAll(multiJobBuilder.getPhaseJobs());
        }
        catch(NullPointerException e) {
            Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "Updating existing multijob with no phase jobs");
            multiJobBuilder.setPhaseJobs(existingPhaseJobs);
        }
        List<String> existingPhaseJobsNames = new ArrayList<>();
        for (PhaseJobsConfig phaseJob : existingPhaseJobs) {
            existingPhaseJobsNames.add(phaseJob.getJobName());
        }


        /* Parse the list of jobs needed by the manage project
         * and add them to the multiJobBuilder */
        List<String> manageProjectJobs = new ArrayList<>();

        for (MultiJobDetail detail : manageProject.getJobs()){
            String newJobName = getBaseName() + "_" + detail.getProjectName();
            Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX newJob = " + newJobName);
            manageProjectJobs.add(newJobName);
            // If the needed job is not yet a phase job of the multi-job project
            if (!existingPhaseJobsNames.contains(newJobName)) {
                Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX newJob to be added= " + newJobName);
                PhaseJobsConfig phase = new PhaseJobsConfig(newJobName,
                                    /*jobproperties*/"",
                                    /*currParams*/true,
                                    /*configs*/null,
                                    PhaseJobsConfig.KillPhaseOnJobResultCondition.NEVER,
                                    /*disablejob*/false,
                                    /*enableretrystrategy*/false,
                                    /*parsingrulespath*/null,
                                    /*retries*/0,
                                    /*enablecondition*/false,
                                    /*abort*/false,
                                    /*condition*/"",
                                    /*buildonly if scm changes*/false,
                                    /*applycond if no scm changes*/false);
                // Add phase job to project
                multiJobBuilder.getPhaseJobs().add(phase);

                // Create and add copy artifact
                String tarFile = "";
                if (isUsingSCM()) {
                    tarFile = ", " + getBaseName() + "_" + detail.getProjectName() + "_build.tar";
                }
                CopyArtifact copyArtifact = new CopyArtifact(newJobName);
                copyArtifact.setOptional(true);
                copyArtifact.setFilter("**/*_rebuild*," +
                                       "execution/*.html, " +
                                       "management/*.html, " +
                                       "xml_data/**" +
                                       tarFile);
                copyArtifact.setFingerprintArtifacts(false);
                BuildSelector bs = new WorkspaceSelector();
                copyArtifact.setSelector(bs);
                project.getBuildersList().add(copyArtifact);
            }
            project.save();
            setTopProject(project); // createProjectPair() uses info based on getTopProject()
            // If the phase job does not already exist, create it
            if (getInstance().getItem(newJobName, project.getParent()) == null) {
                createProjectPair(newJobName, detail, true);
            }
        }

        // Move the last vectorcast builder to the bottom of the builders list.
        project.getBuildersList().remove(reportsVectorCASTCmd);
        project.getBuildersList().add(reportsVectorCASTCmd);
        project.save();

        Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX manageProjectJobs = " + manageProjectJobs);
        Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX existingPhaseJobsNames = " + existingPhaseJobsNames);

        for (PhaseJobsConfig job : existingPhaseJobs) {
            Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX existingPhaseJobs[] = " + job.getJobName());
            if (!manageProjectJobs.contains(job.getJobName())) {
                Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX toBeDeleted = " + job.getJobName());
                Item toDeleteJob = getInstance().getItem(job.getJobName(), project.getParent());
                if (toDeleteJob != null) {
                    // Delete Jenkins project
                    toDeleteJob.delete(); //ConcurrentModificationException?
                    //deleted.add(phaseConfig.getJobName());
                }
                // Remove phase job from project
                multiJobBuilder.getPhaseJobs().remove(job);
                // Remove corresponding CopyArtifact builder from project
                for (Builder builder : project.getBuilders()) {
                    if (builder instanceof CopyArtifact) {
                        CopyArtifact copyBuilder = (CopyArtifact)builder;
                        Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX CopyArtifact found = " + copyBuilder.getProjectName());
                        if (copyBuilder.getProjectName().equals(job.getJobName())) {
                            Logger.getLogger(UpdateMultiJob.class.getName()).log(Level.INFO, "XXX CopyArtifact found - delete = " + copyBuilder.getProjectName());
                            project.getBuildersList().remove(builder);
                        }
                    }
                }

            }
        }
        project.save();
    }
    /**
     * Create new top-level project
     * @return newly created project
     * @throws IOException exception
     */
    @Override
    protected Project createProject() throws IOException {
        String projectName = getMultiJobName();
        String folder = FilenameUtils.getFullPath(jobFullName);
        if (folder != null && !folder.isEmpty()) {
            Folder parentFolder = (Folder)getInstance().getItemByFullName(folder);
            return parentFolder.createProject(MultiJobProject.class, projectName);
        }
        else {
            return getInstance().createProject(MultiJobProject.class, projectName);
        }
    }
    /**
     * Delete job
     * @param jobName job to delete
     * @throws IOException exception
     */
    private void deleteJob(String jobName) throws IOException {
        if (getBaseName().isEmpty()) {
            return;
        }
        try {
            Item job = getInstance().getItemByFullName(jobName);
            if (job != null) {
                    job.delete();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(DeleteJobs.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
