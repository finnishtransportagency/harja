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

    // Haetaan viimeisin onnistunut run tiukalla aikarajauksella, jotta ei kuormiteta turhaan GitHub APIa.
    // Seitsemän vuorokautta kattaa viikonloput ja vastaa harja-uberjar-artifactin säilytysaikaa.
    const cutoffTimestamp = Date.now() - 7 * 24 * 60 * 60 * 1000;
    const createdSince = new Date(cutoffTimestamp).toISOString();
    let latestRun;

    for (let attempt = 1; attempt <= 2; attempt++) {
        const { data: runs } = await github.rest.actions.listWorkflowRuns({
            owner,
            repo,
            workflow_id: workflow.id,
            branch: BRANCH_NAME,
            status: 'success',
            // Haetaan pieni tulosjoukko, josta sortataan ja poimitaan uusin
            per_page: 5,
            created: `>=${createdSince}`,
            // Ohita cache tuoreuden varmistamiseksi
            // https://github.com/octokit/octokit.js/issues/890
            headers: {
                'cache-control': 'no-cache, no-store',
                'if-none-match': '',
            },
        });

        // Katsotaan hakutulosjoukosta, löytyykö uusin onnistunut run.
        // Tässä varmistetaan, että run on luotu aikarajauksen sisällä siltä varalta että GitHub API:ssa on häiriö
        // ja tulosjoukkoon eksyy vanhempia runeja tai muuten virheellisiä tuloksia.
        const currentTimestamp = Date.now();
        const latestRunsSorted = runs.workflow_runs
            .filter(run => {
                const createdAt = Date.parse(run.created_at);
                return createdAt >= cutoffTimestamp && createdAt <= currentTimestamp
                    && run.workflow_id === workflow.id && run.head_branch === BRANCH_NAME
                    && run.status === 'completed' && run.conclusion === 'success';
            })
            .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at));
        latestRun = latestRunsSorted[0];

        if (latestRun) break;

        if (attempt === 1) {
            core.warning(`No valid successful run found since ${createdSince}. Retrying once after two seconds.`);
            // HTTP-virheiden retryt hoitaa kutsuva actions/github-script.
            await new Promise(resolveDelay => setTimeout(resolveDelay, 2000));
        }
    }

    if (!latestRun) {
        throw new Error(
            `No valid successful run found after two attempts: WORKFLOW_FILENAME: ${WORKFLOW_FILENAME}, `
            + `WORKFLOW_ID: ${workflow.id}, BRANCH_NAME: ${BRANCH_NAME}, created >= ${createdSince}. `
            + 'Deployment aborted.'
        );
    }

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
            core.setFailed(`No valid artifact found with the name: ${ARTIFACT_NAME} in the run: ${runId}, run URL: ${latestRun.html_url}`);

            return;
        }
    }

    // Palautetaan onnistuneeseen runiin liittyvä commit SHA ja run ID
    return {
        commitSha,
        runId,
    }
}
