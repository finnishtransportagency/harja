(ns harja.palvelin.integraatiot.paikkatietojarjestelma.ava-test
  (:require
    [com.stuartsierra.component :as component]
    [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.testi :as testi]
    [harja.palvelin.integraatiot.paikkatietojarjestelma.ava :as ava]))


(defn aja-tiedoston-haku
  "REPL-testiajofunktio"
  []
  (let [testitietokanta (tietokanta/luo-tietokanta testi/testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (ava/hae-tiedosto
      integraatioloki
      testitietokanta
      "tieverkon-haku"
      "http://185.26.50.104/Tieosoiteverkko.zip"
      "/Users/mikkoro/Desktop/Tieosoiteverkko.zip")))

