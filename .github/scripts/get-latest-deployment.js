// https://docs.github.com/en/rest/deployments/deployments?apiVersion=2022-11-28#list-deployments

module.exports = async ({ github, context, core }) => {
    const owner = context.repo.owner;
    const repo = context.repo.repo;
    const DEPLOYMENT_REF = process.env.DEPLOYMENT_REF;
    const DEPLOYMENT_ENVIRONMENT = process.env.DEPLOYMENT_ENVIRONMENT;
    // Optional: Filtteröi myös SHA:n perusteella
    const DEPLOYMENT_SHA = process.env.DEPLOYMENT_SHA;

    // Tarkasta pakolliset ympäristömuuttujat
    if (!DEPLOYMENT_REF) {
        core.DEPLOYMENT_REF('ENV DEPLOYMENT_REF is required');

        return;
    }

    if (!DEPLOYMENT_ENVIRONMENT) {
        core.setFailed('ENV DEPLOYMENT_ENVIRONMENT is required');

        return;
    }

    const { data: deployments } = await github.rest.repos.listDeployments({
        owner,
        repo,
        environment: DEPLOYMENT_ENVIRONMENT,
        // Esimerkiksi branchin nimi
        ref: DEPLOYMENT_REF,
        // SHA, joka on tallennettu deploymentin luomisen yhteydessä
        sha: DEPLOYMENT_SHA,
        per_page: 1,
    });

    if (deployments.length === 0) {
        core.warning(`No deployments found for the DEPLOYMENT_REF: ${DEPLOYMENT_REF}, DEPLOYMENT_SHA: ${DEPLOYMENT_SHA} and DEPLOYMENT_ENVIRONMENT: ${DEPLOYMENT_ENVIRONMENT}`);

        return;
    }

    const latestDeployment = deployments[0];
    const deployment_id = latestDeployment.id;

    // Hae viimeisimmän deploymentin tila
    const { data: deploymentStatuses } = await github.rest.repos.listDeploymentStatuses({
        owner,
        repo,
        deployment_id,
        per_page: 1,
    });

    const status = deploymentStatuses[0].state;

    return {
        sha: latestDeployment.sha,
        description: latestDeployment.description,
        deployment_id,
        status,
    }
}
