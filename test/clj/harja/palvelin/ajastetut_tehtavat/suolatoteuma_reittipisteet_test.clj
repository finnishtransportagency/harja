(ns harja.palvelin.ajastetut-tehtavat.suolatoteuma-reittipisteet-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.ajastetut-tehtavat.yleiset-ajastukset :as yleiset-ajastukset]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.fim-test :as fim-test]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.pvm :refer [luo-pvm]]
            [clj-time.core :as t]
            [clj-time.coerce :as t-coerce]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.pvm :as pvm])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

;; Suolatoteuma_reittipiste -tauluun siirretään joka yö (nyt 00:45) muuttuneiden rajoitusalueiden vuoksi sen urakan
;; käsilläolevan hoitovuoden kaikki toteumen_reittipisteet.

(deftest suolatoteuma-rajoitusalueen-paivitys-siirto-toimii
  (let [testitietokanta (:db jarjestelma)
        ;; Käytetään oulu MHU urakkaa
        urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
        ;; Poista suolatoteuma_reittipiste -taulusta kaikki urakka-id:n rivit
        suolatoteumat-aluksi (q-map (format "SELECT * FROM suolatoteuma_reittipiste WHERE toteuma in (SELECT id FROM toteuma where urakka = %s)" urakka-id))
        _ (u (format "DELETE FROM suolatoteuma_reittipiste WHERE toteuma in (SELECT id FROM toteuma where urakka = %s)" urakka-id))

        ;; Päivitä urakan kaikki rajoitusalueet muka
        _ (u (format "UPDATE rajoitusalue SET tierekisteri_muokattu = true WHERE urakka_id = %s " urakka-id))
        urakat (suolarajoitus-kyselyt/hae-rajoitusaluetta-muokanneet-urakat testitietokanta)
        _ (is (= 1 (count urakat)))
        _ (is (= urakka-id (:urakka_id (first urakat))))

        ;; Kutsu ajastettua tehtävää
        _ (yleiset-ajastukset/paivita-mahdolliset-suolatoteumat testitietokanta)
        urakat-ajastuksen-jalkeen (suolarajoitus-kyselyt/hae-rajoitusaluetta-muokanneet-urakat testitietokanta)
        _ (is (= 0 (count urakat-ajastuksen-jalkeen)))

        ;; Tarkista, että suolatoteuma_reittipiste -taulussa on nyt urakan kaikki toteumat
        ;; Haetaan yksi toteumaid, jotta voidaan odottaa vain minimimäärä aikaa
        suolatoteumat (q-map (format "SELECT * FROM suolatoteuma_reittipiste WHERE toteuma in (SELECT id FROM toteuma where urakka = %s)" urakka-id))]
    (is (= (apply + (map :maara suolatoteumat-aluksi)) (apply + (map :maara suolatoteumat))))))



