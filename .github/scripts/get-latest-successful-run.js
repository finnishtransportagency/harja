module.exports = async ({ github, context, core }) => {
    const owner = context.repo.owner;
    const repo = context.repo.repo;
    const WORKFLOW_ID = process.env.WORKFLOW_ID;
    const BRANCH_NAME = process.env.BRANCH_NAME;

    const { data: repoWorkflows } = await github.rest.actions.listRepoWorkflows({
        owner,
        repo
    });

    const workflow = repoWorkflows.workflows.find(w => w.path.includes(WORKFLOW_ID));

    if (!workflow) {
        core.setFailed(`No workflow found with the specified WORKFLOW_ID: ${WORKFLOW_ID}`);

        return;
    }

    const { data: runs } = await github.rest.actions.listWorkflowRuns({
        owner: context.repo.owner,
        repo: context.repo.repo,
        workflow_id: WORKFLOW_ID,
        branch: BRANCH_NAME,
        status: 'success',
        per_page: 1
    });

    console.log(JSON.stringify(runs));

    if (runs.total_count === 0) {
        core.setFailed(`No runs found for the WORKFLOW_ID: ${WORKFLOW_ID}`);

        return;
    }

    return {
        commit_sha: runs.workflow_runs[0].head_commit.id,
        run_id: runs.workflow_runs[0].id
    }
}
