(ns harja.palvelin.integraatiot.paikkatietojarjestelma.alk-test
  (:require
    [com.stuartsierra.component :as component]
    [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.testi :as testi]
    [harja.palvelin.integraatiot.paikkatietojarjestelma.alk :as alk]))

(defn aja-tiedoston-muutospaivamaara-kysely []
  "REPL-testiajofunktio"
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (alk/hae-tiedoston-muutospaivamaara
      integraatioloki
      "tieverkon-muutospaivamaaran-haku"
      "http://185.26.50.104/Tieosoiteverkko.zip")))

(defn aja-tiedoston-haku []
  "REPL-testiajofunktio"
  (let [testitietokanta (apply tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (alk/hae-tiedosto
      integraatioloki
      "tieverkon-haku"
      "http://185.26.50.104/Tieosoiteverkko.zip"
      "/Users/mikkoro/Desktop/Tieosoiteverkko.zip")))