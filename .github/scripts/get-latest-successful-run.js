module.exports = async ({ github, context, core }) => {
    const owner = context.repo.owner;
    const repo = context.repo.repo;
    const WORKFLOW_FILENAME = process.env.WORKFLOW_FILENAME;
    const BRANCH_NAME = process.env.BRANCH_NAME;
    const ARTIFACT_NAME = process.env.CHECK_ARTIFACT_WITH_NAME;

    // Tarkasta pakolliset ympäristömuuttujat
    if (!WORKFLOW_FILENAME) {
        core.setFailed('ENV WORKFLOW_FILENAME is required');

        return;
    }

    if (!BRANCH_NAME) {
        core.setFailed('ENV BRANCH_NAME is required');

        return;
    }


    const { data: repoWorkflows } = await github.rest.actions.listRepoWorkflows({
        owner,
        repo,
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
        per_page: 1,
    });

    if (runs.total_count === 0) {
        core.setFailed(`No runs found for the WORKFLOW_ID: ${workflow.id} and BRANCH_NAME: ${BRANCH_NAME}`);

        return;
    }

    const latestRun = runs.workflow_runs[0];
    const commitSha = latestRun.head_commit.id;
    const runId = latestRun.id;

    // Tarkasta onko runissa haluttua artifactia ja että se ei ole vanhentunut
    if (!!ARTIFACT_NAME) {
        const { data: runArtifacts } = await github.rest.actions.listWorkflowRunArtifacts({
            owner: context.repo.owner,
            repo: context.repo.repo,
            run_id: runId,
        });

        const artifact = runArtifacts.artifacts.find(a => a.name === ARTIFACT_NAME);

        if (!artifact || artifact.expired) {
            core.setFailed(`No valid artifact found with the name: ${ARTIFACT_NAME} in the run: ${runId}`);

            return;
        }
    }

    // Palautetaan onnistuneeseen runiin liittyvä commit SHA ja run ID
    return {
        commitSha,
        runId,
    }
}
