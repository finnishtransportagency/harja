'use strict';

// Testit: node --test .github/scripts/get-latest-successful-run.test.js
const { test } = require('node:test');
const assert = require('node:assert/strict');
const getLatestSuccessfulRun = require('./get-latest-successful-run.js');

const NOW = Date.parse('2026-10-16T07:00:00.000Z');
const DAY = 24 * 60 * 60 * 1000;

function run(overrides = {}) {
    return {
        id: 123,
        workflow_id: 10,
        head_branch: 'develop',
        head_commit: { id: 'testi-commit' },
        status: 'completed',
        conclusion: 'success',
        event: 'push',
        created_at: new Date(NOW - DAY).toISOString(),
        html_url: 'https://github.com/omistaja/projekti/actions/runs/123',
        ...overrides,
    };
}

function setup(t, responses = [{ total_count: 1, workflow_runs: [run()] }]) {
    const originalEnv = process.env;
    process.env = { ...originalEnv, WORKFLOW_FILENAME: 'harja_build_and_deploy.yml', BRANCH_NAME: 'develop' };

    delete process.env.CHECK_ARTIFACT_WITH_NAME;

    t.after(() => {
        process.env = originalEnv;
    });

    t.mock.method(Date, 'now', () => NOW);

    const delay = t.mock.method(globalThis, 'setTimeout', (resume) => {
        resume();
    });

    const actions = {
        listRepoWorkflows: t.mock.fn(async () => ({
            data: {
                workflows: [{ id: 10, path: '.github/workflows/harja_build_and_deploy.yml' }],
            },
        })),
        listWorkflowRuns: t.mock.fn(async () => {
            assert.ok(responses.length > 0, 'Unexpected extra API call');
            const response = responses.shift();
            if (response instanceof Error) throw response;
            return { data: response };
        }),
        listWorkflowRunArtifacts: t.mock.fn(async () => ({
            data: {
                artifacts: [{ name: 'harja-uberjar', expired: false }],
            },
        })),
    };

    const core = { setFailed: t.mock.fn(), warning: t.mock.fn(), error: t.mock.fn() };

    return {
        actions,
        core,
        delay,
        execute: () => getLatestSuccessfulRun({
            github: { rest: { actions } },
            context: { repo: { owner: 'omistaja', repo: 'projekti' } },
            core,
        }),
    };
}

test('rajaa haun seitsemään vuorokauteen', async (t) => {
    const { execute, actions, delay } = setup(t);

    assert.deepEqual(await execute(), { commitSha: 'testi-commit', runId: 123 });
    assert.deepEqual(actions.listWorkflowRuns.mock.calls[0].arguments[0], {
        owner: 'omistaja',
        repo: 'projekti',
        workflow_id: 10,
        branch: 'develop',
        status: 'success',
        per_page: 5,
        created: '>=2026-10-09T07:00:00.000Z',
        headers: {
            'cache-control': 'no-cache, no-store',
            'if-none-match': '',
        },
    });
    assert.equal(actions.listWorkflowRuns.mock.callCount(), 1);
    assert.equal(delay.mock.callCount(), 0);
});

test('valitsee uusimman created_at-aikaleiman eikä luota vastausjärjestykseen tai updated_at-kenttään', async (t) => {
    const { execute } = setup(t, [{
        total_count: 2500, workflow_runs: [
            // Laitetaan vanhempi run ensimmäiseksi >:D... Kiitos, GitHub API!
            run({
                id: 1,
                created_at: new Date(NOW - 2 * DAY).toISOString(),
                updated_at: new Date(NOW).toISOString(),
            }),
            run({ id: 2, created_at: new Date(NOW - 1000).toISOString() }),
            run(),
        ],
    }]);
    assert.equal((await execute()).runId, 2);
});

test('valitsee joukosta kelvollisen runin vaikka samassa vastauksessa on epäkelpoja ehdokkaita', async (t) => {
    const { execute } = setup(t, [{
        workflow_runs: [
            run({ id: 1, created_at: 'ei-aikaleima' }),
            run({ id: 2, created_at: new Date(NOW).toISOString(), conclusion: 'failure' }),
            run({ id: 3, created_at: new Date(NOW).toISOString(), head_branch: 'muu-branch' }),
            run({ id: 4, created_at: new Date(NOW - 21 * DAY).toISOString() }),
            run(),
        ],
    }]);
    assert.equal((await execute()).runId, 123);
});

test('ei tulkitse total_count-arvoa tulosten olemassaolon mittariksi', async (t) => {
    const { execute } = setup(t, [{ total_count: 0, workflow_runs: [run()] }]);
    assert.equal((await execute()).runId, 123);
});

test('hyväksyy myös manuaalisesti käynnistetyn onnistuneen runin', async (t) => {
    const { execute } = setup(t, [{ workflow_runs: [run({ event: 'workflow_dispatch' })] }]);
    assert.equal((await execute()).runId, 123);
});

test('hyväksyy täsmälleen aikarajalla luodun runin', async (t) => {
    const { execute } = setup(t, [{ workflow_runs: [run({ created_at: new Date(NOW - 7 * DAY).toISOString() })] }]);
    assert.equal((await execute()).runId, 123);
});

const invalidRuns = [
    ['kolme viikkoa vanha run', { created_at: new Date(NOW - 21 * DAY).toISOString() }],
    ['millisekunnin liian vanha run', { created_at: new Date(NOW - 7 * DAY - 1).toISOString() }],
    ['tulevaisuuden aikaleima', { created_at: new Date(NOW + DAY).toISOString() }],
    ['virheellinen aikaleima', { created_at: 'ei-aikaleima' }],
    ['puuttuva aikaleima', { created_at: undefined }],
    ['väärä branch', { head_branch: 'muu-branch' }],
    ['väärä workflow', { workflow_id: 20 }],
    ['keskeneräinen run', { status: 'in_progress' }],
    ['epäonnistunut run', { conclusion: 'failure' }],
];

for (const [description, runOverrides] of invalidRuns) {
    test(`hylkää epäkelvon vastauksen: ${description}, ja uusii haun kerran`, async (t) => {
        const response = { total_count: 1, workflow_runs: [run(runOverrides)] };
        const { execute, actions, core, delay } = setup(t, [response, response]);

        await assert.rejects(execute(), /No valid successful run found.*/);

        assert.equal(actions.listWorkflowRuns.mock.callCount(), 2);
        assert.equal(actions.listWorkflowRunArtifacts.mock.callCount(), 0);
        assert.equal(core.warning.mock.callCount(), 1);
        assert.equal(delay.mock.callCount(), 1);
        assert.equal(delay.mock.calls[0].arguments[1], 2000);
    });
}

test('tyhjä lista ei kelpaa vaikka total_count olisi positiivinen', async (t) => {
    const response = { total_count: 1000, workflow_runs: [] };
    const { execute, actions } = setup(t, [response, response]);

    await assert.rejects(execute(), /No valid successful run found/);
    assert.equal(actions.listWorkflowRuns.mock.callCount(), 2);
});

test('tyhjä ensimmäinen vastaus ja tuore toinen vastaus onnistuvat', async (t) => {
    const { execute, actions } = setup(t, [
        { total_count: 0, workflow_runs: [] },
        { total_count: 1, workflow_runs: [run()] },
    ]);
    assert.equal((await execute()).runId, 123);
    assert.deepEqual(actions.listWorkflowRuns.mock.calls[0].arguments, actions.listWorkflowRuns.mock.calls[1].arguments);
});

test('vanhentunut ensimmäinen vastaus korvautuu tuoreella toisella vastauksella', async (t) => {
    const { execute } = setup(t, [
        { workflow_runs: [run({ created_at: new Date(NOW - 21 * DAY).toISOString() })] },
        { workflow_runs: [run()] },
    ]);
    assert.equal((await execute()).runId, 123);
});

test('hyväksyy retry-odotuksen aikana luodun runin muuttamatta haun alarajaa', async (t) => {
    const { execute, actions, delay } = setup(t, [
        { workflow_runs: [] },
        { workflow_runs: [run({ created_at: new Date(NOW + 1000).toISOString() })] },
    ]);
    delay.mock.mockImplementation((resume) => {
        Date.now.mock.mockImplementation(() => NOW + 2000);
        resume();
    });
    assert.equal((await execute()).runId, 123);
    assert.deepEqual(actions.listWorkflowRuns.mock.calls[0].arguments, actions.listWorkflowRuns.mock.calls[1].arguments);
});

test('API-virhe välittyy kutsujalle ilman uutta omaa HTTP-retry-kerrosta', async (t) => {
    const error = new Error('API service unavailable');
    const { execute, actions } = setup(t, [error]);
    await assert.rejects(execute(), (result) => result === error);
    assert.equal(actions.listWorkflowRuns.mock.callCount(), 1);
});

test('tarkistaa edelleen valitun runin artifactin', async (t) => {
    const { execute, actions, core } = setup(t);
    process.env.CHECK_ARTIFACT_WITH_NAME = 'harja-uberjar';
    assert.equal((await execute()).runId, 123);
    assert.equal(core.setFailed.mock.callCount(), 0);
    assert.equal(actions.listWorkflowRunArtifacts.mock.calls[0].arguments[0].run_id, 123);
});

test('ei vaihda vanhempaan runiin jos uusimman artifact on vanhentunut', async (t) => {
    const { execute, actions, core } = setup(t, [{
        workflow_runs: [
            run({ id: 100, created_at: new Date(NOW - 2 * DAY).toISOString() }),
            run(),
        ],
    }]);
    process.env.CHECK_ARTIFACT_WITH_NAME = 'harja-uberjar';
    actions.listWorkflowRunArtifacts.mock.mockImplementation(async ({ run_id }) => ({
        data: {
            artifacts: [{ name: 'harja-uberjar', expired: run_id === 123 }],
        },
    }));
    assert.equal(await execute(), undefined);
    assert.equal(core.setFailed.mock.callCount(), 1);
    assert.equal(actions.listWorkflowRuns.mock.callCount(), 1);
    assert.equal(actions.listWorkflowRunArtifacts.mock.callCount(), 1);
    assert.equal(actions.listWorkflowRunArtifacts.mock.calls[0].arguments[0].run_id, 123);
});

