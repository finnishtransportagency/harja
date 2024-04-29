module.exports = async ({ github, context, core }) => {
    const owner = context.repo.owner;
    const repo = context.repo.repo;
    const WORKFLOW_FILENAME = process.env.WORKFLOW_FILENAME;
    const BRANCH_NAME = process.env.BRANCH_NAME;

    const { data: repoWorkflows } = await github.rest.actions.listRepoWorkflows({
        owner,
        repo
    });

    const workflow = repoWorkflows.workflows.find(w => w.path.includes(WORKFLOW_FILENAME));

    if (!workflow) {
        core.setFailed(`No workflow found with the specified WORKFLOW_FILENAME: ${WORKFLOW_FILENAME}`);

        return;
    }

    // Palauttaa viimeisimmän määrätyssä branchissa onnistuneen workflow-runin tiedot
    const { data: runs } = await github.rest.actions.listWorkflowRuns({
        owner: context.repo.owner,
        repo: context.repo.repo,
        workflow_id: workflow.id,
        branch: BRANCH_NAME,
        status: 'success',
        per_page: 1
    });

    if (runs.total_count === 0) {
        core.setFailed(`No runs found for the WORKFLOW_ID: ${workflow.id} and BRANCH_NAME: ${BRANCH_NAME}`);

        return;
    }

    // Palautetaan onnistuneeseen runiin liittyvä commit SHA ja run ID
    return {
        commit_sha: runs.workflow_runs[0].head_commit.id,
        run_id: runs.workflow_runs[0].id
    }
}
