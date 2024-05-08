/**
 *
 * Scriptiä käytetään korjaamaan GitHub Actionsin 'environment'-toiminnallisuuden puutteita.
 * Kutsu scriptiä workflowssa deploymentin tehneen jobin jälkeen käyttämällä "actions/github-script" actionia.
 * Vaaditut oikeudet:
 *   * deployments: write
 *
 * Rationale:
 *  Jos teet jollakin workflowlla manuaalisesti deploymentin johonkin ympäristöön ja workflow hyödyntää Github Actionsin
 *  'environment'-asetusta, niin deploymentin yhteydessä luodaan automaattisesti deployment-tapahtuma Deployments-välilehdelle.
 *  GitHub actionsin tekemä deployment-tapahtuma osoittaa todennäköisesti päähaaran viimeisimpään refiin workflow:n
 *  ajohetkellä. Tämä muodostuu ongelmaksi erityisesti niissä tilanteissa, joissa workflow:n halutaan tekevän deploymentin
 *  jostakin tietystä commitista.
 *  Tällöin muodostuu ristiriita:
 *    Deployments-välilehden deployment historiassa näkyy uusimpana deploymenttina päähaaran viimeisin commit, vaikka
 *    workflow teki oikeasti deploymentin jostakin toisesta commitista.
 *  GitHub actionsin puutteiden takia automaattiselle environment-toiminnallisuudelle ei voi antaa lisätietoa siitä,
 *  mihin committiin deployment oikeasti liittyy, joten asia täytyy ratkaista muilla keinoilla.
 *  Environment-toiminnallisuus itsessään on hyödyllinen mm. salaisuuksien hyödyntämisen kannalta, joten siitä ei
 *  kannata luopua.
 *
 *  Tämä skripti korjaa tämän ongelman seuraavasti:
 *    - Haetaan viimeisin deployment halutusta environmentista, joka löytyy workflown triggeröineen SHA:n
 *      perusteella.
 *    - Luodaan uusi deployment-tapahtuma, joka osoittaa haluttuun commitiin ja ympäristöön.
 *    - Poisteetaan alkuperäinen deployment-tapahtuma.
 *
 */

module.exports = async ({ github, context, core }) => {
    const owner = context.repo.owner;
    const repo = context.repo.repo;
    const DEPLOYMENT_ENVIRONMENT = process.env.DEPLOYMENT_ENVIRONMENT;
    const DEPLOYMENT_REF = process.env.DEPLOYMENT_REF;
    const DEPLOYMENT_DESCRIPTION = process.env.DEPLOYMENT_DESCRIPTION;

    // Tarkasta pakolliset ympäristömuuttujat
    if (!DEPLOYMENT_ENVIRONMENT) {
        core.setFailed('ENV DEPLOYMENT_ENVIRONMENT is required');

        return;
    }

    if (!DEPLOYMENT_REF) {
        core.setFailed('ENV DEPLOYMENT_REF is required');

        return;
    }

    if (!DEPLOYMENT_DESCRIPTION) {
        core.setFailed('ENV DEPLOYMENT_DESCRIPTION is required');

        return;
    }

    const { data: deployments } = await github.rest.repos.listDeployments({
        owner,
        repo,
        environment: DEPLOYMENT_ENVIRONMENT,
        // Hae deploymentit, jotka liittyvät workflown triggeröineeseen SHA:han
        sha: context.sha,
        per_page: 1,
    });

    if (deployments.length === 0) {
        core.setFailed(`No deployments found for the DEPLOYMENT_ENVIRONMENT: ${DEPLOYMENT_ENVIRONMENT} and SHA: ${context.sha}`);

        return;
    }

    const originalDeployment = deployments[0];

    console.log(`Found deployment ID: ${originalDeployment.id}, SHA: ${originalDeployment.sha}, Updated: ${originalDeployment.updated_at} Description: ${originalDeployment.description}`);

    const { data: deploymentStatuses } = await github.rest.repos.listDeploymentStatuses({
        owner,
        repo,
        deployment_id: originalDeployment.id,
        per_page: 1,
    });

    const originalDeploymentStatus = deploymentStatuses[0].state;

    console.log(`Found deployment status for deployment: ${originalDeployment.id}. Status: ${originalDeploymentStatus}`)

    // Luo uusi deployment, joka korvaa GitHub Actionsin automaattisesti luoman deploymentin annetuilla tiedoilla
    // https://docs.github.com/en/rest/deployments/deployments?apiVersion=2022-11-28#create-a-deployment
    const newDeploymentResponse = await github.rest.repos.createDeployment({
        owner,
        repo,
        // Käytetään alkuperäisen deploymentin enveronmenttia uuden luomisessa
        environment: originalDeployment.environment,
        // Uusi deployment asetetaan osoittaamaan haluttuun refiin (esim. commit SHA)
        ref: DEPLOYMENT_REF,
        description: DEPLOYMENT_DESCRIPTION,
        auto_merge: false,
        // Skipataan status checkit, koska nyt korvataan aiempi deployment
        required_contexts: [],
    });

    if (newDeploymentResponse.status !== 201) {
        core.setFailed(`Failed to create new deployment. Status: ${newDeploymentResponse.status}, data: ${newDeploymentResponse.data}`);

        return;
    }

    const newDeployment = newDeploymentResponse.data;

    const newDeploymentStatusResponse = await github.rest.repos.createDeploymentStatus({
        owner,
        repo,
        deployment_id: newDeployment.id,
        // Käytetään alkuperäisen deploymentin tilaa, tai 'success'
        state: originalDeploymentStatus ?? 'success',
        // https://docs.github.com/en/actions/learn-github-actions/variables#default-environment-variables
        log_url: `${context.serverUrl}/${owner}/${repo}/actions/runs/${context.runId}`,
        // Asetetaan uudelle deploymentille oma kuvaus
        description: DEPLOYMENT_DESCRIPTION,
        auto_inactive: true,
    });

    if (newDeploymentStatusResponse.status !== 201) {
        core.setFailed(`Failed to create new deployment status. Status: ${newDeploymentStatusResponse.status}, data: ${newDeploymentStatusResponse.data}`);

        return;
    }

    console.log(`Created new deployment ID: ${newDeployment.id}, SHA: ${newDeployment.sha}, Description: ${newDeployment.description}`)


    // Merkitään alkuperäinen deployment inaktiiviseksi ja poistetaan se
    // HUOM: Vain inaktiiviset deploymentit voidaan poistaa
    const originalDeploymentStatusUpdateResponse = await github.rest.repos.createDeploymentStatus({
        owner,
        repo,
        deployment_id: originalDeployment.id,
        state: 'inactive',
    });

    if (originalDeploymentStatusUpdateResponse.status !== 201) {
        core.setFailed(`Failed to update the original deployment status. Status: ${originalDeploymentStatusUpdateResponse.status}`);

        return;
    }

    const deleteOriginalDeploymentResponse = await github.rest.repos.deleteDeployment({
        owner,
        repo,
        deployment_id: originalDeployment.id,
    });

    if (deleteOriginalDeploymentResponse.status !== 204) {
        core.setFailed(`Failed to delete original deployment ID: ${originalDeployment.id}.`);

        return;
    }


    // Palautetaan uuden deploymentin tiedot
    return {
        deployment_id: newDeployment.id,
    }
}
