-- Talvihoitoreitityksille tietokantataulut
CREATE TABLE IF NOT EXISTS talvihoitoreitti (
                                                id SERIAL PRIMARY KEY,
                                                nimi VARCHAR(255) NOT NULL,
                                                urakka_id INTEGER NOT NULL,
                                                muokattu TIMESTAMP,
                                                muokkaaja INTEGER,
                                                luotu TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                luoja INTEGER
);

CREATE TABLE IF NOT EXISTS talvihoitoreitti_kalusto (
                                                        id SERIAL PRIMARY KEY,
                                                        talvihoitoreitti_id INTEGER NOT NULL,
                                                        kalustotyyppi VARCHAR(255) NOT NULL,
                                                        maara INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS talvihoitoreitti_reitti (
                                                       id SERIAL PRIMARY KEY,
                                                       talvihoitoreitti_id INTEGER NOT NULL,
                                                       tie INTEGER NOT NULL,
                                                       alkuosa INTEGER NOT NULL,
                                                       alkuetaisyys INTEGER NOT NULL,
                                                       loppuosa INTEGER,
                                                       loppuetaisyys INTEGER,
                                                       hoitoluokka INTEGER NOT NULL,
                                                       pituus INTEGER, -- metreinä
                                                       reitti geometry
);

-- Uusi apiavain
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'lisaa-talvihoitoreitti');
