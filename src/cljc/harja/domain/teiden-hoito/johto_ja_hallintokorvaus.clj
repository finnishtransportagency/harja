(ns harja.domain.teiden-hoito.johto-ja-hallintokorvaus
  (:require [harja.palvelin.palvelut.budjettisuunnittelu :as kustannussuunnittelu]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.tyokalut.merkkijono :as merkkijono]
            [clojure.string :as str]
            [clojure.set :as set]
            [harja.pvm :as pvm]
            [clj-time.format :as df]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.walk :as walk])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn hae-johto-ja-hallintokorvaus-kuukausittain
  [db user urakka-id]
  (let [(kustannussuunnittelu/hae-urakan-johto-ja-hallintokorvaukset db user {:urakka-id urakka-id})]))

