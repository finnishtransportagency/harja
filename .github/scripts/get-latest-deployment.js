// https://docs.github.com/en/rest/deployments/deployments?apiVersion=2022-11-28#list-deployments

module.exports = async ({ github, context, core }) => {
    const owner = context.repo.owner;
    const repo = context.repo.repo;
    const BRANCH_NAME = process.env.BRANCH_NAME;
    const TARGET_ENVIRONMENT = process.env.TARGET_ENVIRONMENT;

    // Tarkasta pakolliset ympäristömuuttujat
    if (!BRANCH_NAME) {
        core.setFailed('ENV BRANCH_NAME is required');

        return;
    }

    if (!TARGET_ENVIRONMENT) {
        core.setFailed('ENV TARGET_ENVIRONMENT is required');

        return;
    }

    const { data: deployments } = await github.rest.repos.listDeployments({
        owner,
        repo,
        environment: TARGET_ENVIRONMENT,
        ref: BRANCH_NAME,
        per_page: 1,
    });

    if (deployments.length === 0) {
        core.setFailed(`No deployments found for the BRANCH_NAME: ${BRANCH_NAME} and TARGET_ENVIRONMENT: ${TARGET_ENVIRONMENT}`);

        return;
    }

    const latestDeployment = deployments[0];
    const sha = latestDeployment.sha;
    const description = latestDeployment.description;
    const id = latestDeployment.id;
    const task = latestDeployment.task;

    return {
        sha,
        description,
        task,
        id,
    }
}
