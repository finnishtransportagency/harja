(ns harja.kyselyt.tilannekuva
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]))

(defn muunna-reitti [{reitti :reitti :as rivi}]
  (assoc rivi
         :reitti (geo/pg->clj reitti)))

(defqueries "harja/kyselyt/tilannekuva.sql"
  {:positional? true})

(declare hallintayksikoiden-urakat hae-tyokoneselitteet urakoitsijan-urakat hae-ilmoitukset
  hae-paikkaukset-nykytilanteeseen hae-paikkaukset-historiakuvaan hae-paikkauskohteet-tilannekuvaan
  hae-paallystysten-viimeisin-muokkaus hae-laatupoikkeamat hae-turvallisuuspoikkeamat hae-toimenpidekoodit
  hae-toteumat hae-tarkastukset hae-tyokonereitit-kartalle hae-paallystysten-reitit hae-tietyomaat
  hae-viimeisin-toteuma hae-toteumien-selitteet elinvoimakeskusten-urakat hae-toteumien-asiat)
