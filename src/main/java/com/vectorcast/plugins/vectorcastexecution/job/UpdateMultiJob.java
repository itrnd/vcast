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
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
        deleted = new ArrayList<>();
        String projectName = FilenameUtils.getFullPath(jobFullName) + getMultiJobName();
        // Delete all existing phase jobs first.
        MultiJobProject project = (MultiJobProject)getInstance().getItemByFullName(projectName);
        for (Builder builder : project.getBuilders()) {
            if (builder instanceof MultiJobBuilder) {
                MultiJobBuilder multiJobBuilder = (MultiJobBuilder) builder;
                if (multiJobBuilder.getPhaseJobs().size() > 0) {
                    for (PhaseJobsConfig phaseConfig : multiJobBuilder.getPhaseJobs()) {
                        //Item phaseJob = getInstance().getItem(phaseConfig.getJobName(), project.getParent());
                        //if (phaseJob != null) {
                            //phaseJob.delete(); //ConcurrentModificationException
                            deleted.add(phaseConfig.getJobName());
                        //}
                    }
                }
            }
        }
        for (String name : deleted) {
            Item job = getInstance().getItem(name, project.getParent());
            if (job != null) {
                job.delete();
            }
        }
        // Delete existing multijob
        //deleteJob(projectName);
        project.delete();
        // Create all other projects
        create(true);
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
