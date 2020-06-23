(ns harja.tiedot.urakka.toteumat
  "Tämä nimiavaruus hallinnoi urakan toteumien tietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

; FIXME Tämä nimiavaruus hallinoi useaa Toteumat-välilehden alivälilehtiä. Pitäisi refactoroida niin, että
; jokaisella näkymällä olisi oma tiedot-namespace kaverina.

(def maarien-toteumat-nakymassa? (atom false))
(defonce erilliskustannukset-nakymassa? (atom false))

(defn hae-urakan-toteuma [urakka-id toteuma-id]
  (k/post! :urakan-toteuma
           {:urakka-id urakka-id
            :toteuma-id toteuma-id}))

(defn hae-urakan-toteumien-tehtavien-summat [urakka-id sopimus-id [alkupvm loppupvm] tyyppi toimenpide-id tehtava-id]
  (log "Haetaan: toimenpide-id tehtava-id")
  (k/post! :urakan-toteumien-tehtavien-summat
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :tyyppi tyyppi
            :toimenpide-id toimenpide-id
            :tehtava-id tehtava-id}))

(defn hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla [urakka-id sopimus-id [alkupvm loppupvm] tyyppi toimenpidekoodi]
  (log "TOT Haetaan urakan toteutuneet tehtävät toimenpidekoodilla: " toimenpidekoodi)
  (k/post! :urakan-toteutuneet-tehtavat-toimenpidekoodilla
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :tyyppi tyyppi
            :toimenpidekoodi toimenpidekoodi}))

(defn tallenna-toteuma-ja-yksikkohintaiset-tehtavat [toteuma]
  (k/post! :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat toteuma))

(defn paivita-yk-hint-toteumien-tehtavat [urakka-id sopimus-id [alkupvm loppupvm] tyyppi tehtavat toimenpide-id]
  (k/post! :paivita-yk-hint-toteumien-tehtavat {:urakka-id urakka-id
                                                :sopimus-id sopimus-id
                                                :alkupvm alkupvm
                                                :loppupvm loppupvm
                                                :tyyppi tyyppi
                                                :tehtavat tehtavat
                                                :toimenpide-id toimenpide-id}))

(defn hae-urakan-erilliskustannukset [urakka-id [alkupvm loppupvm]]
  (k/post! :urakan-erilliskustannukset
           {:urakka-id urakka-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))

(defn hae-urakan-maarien-toteumat [urakka-id tehtavaryhma-idt]
  (k/post! :urakan-maarien-toteumat
           {:urakka-id urakka-id
            :tehtavaryhma-idt tehtavaryhma-idt}))

(defn hae-maarien-toteumat-sivun-toimenpiteet []
  (k/get! :tehtavat))

(defn tallenna-erilliskustannus [ek]
  (k/post! :tallenna-erilliskustannus ek))

(defn tallenna-toteuma-ja-toteumamateriaalit! [toteuma toteumamateriaalit hoitokausi sopimus-id]
  (k/post! :tallenna-toteuma-ja-toteumamateriaalit {:toteuma toteuma
                                                    :toteumamateriaalit toteumamateriaalit
                                                    :hoitokausi hoitokausi
                                                    :sopimus sopimus-id}))


(defn hae-urakan-toteutuneet-muut-tyot [urakka-id sopimus-id [alkupvm loppupvm]]
  (log "tiedot: hae urakan muut työt" urakka-id sopimus-id alkupvm loppupvm)
  (k/post! :urakan-toteutuneet-muut-tyot
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))

(def ilmoitus-jarjestelman-luoma-toteuma
  "Tietojärjestelmästä tulleen toteuman muokkaus ei ole sallittu.")

(def +muun-tyon-tyypit+
  [:muutostyo :lisatyo :akillinen-hoitotyo :vahinkojen-korjaukset])

(def +valitse-tyyppi+
  "- Valitse tyyppi -")

(defn muun-tyon-tyypin-teksti-genetiivissa [avainsana]
  "Muun työn tyypin teksti avainsanaa vastaan"
  (case avainsana
    :muutostyo "muutostyön"
    :lisatyo "lisätyön"
    :akillinen-hoitotyo "äkillisen hoitotyön"
    :vahinkojen-korjaukset "vahinkojen korjaukset"
    "työn jonka tyyppi on tuntematon"))

(defn muun-tyon-tyypin-teksti [avainsana]
  "Muun työn tyypin teksti avainsanaa vastaan"
  (case avainsana
    :muutostyo "Muutostyö"
    :lisatyo "Lisätyö"
    :akillinen-hoitotyo "Äkillinen hoitotyö"
    :vahinkojen-korjaukset "Vahinkojen korjaukset"
    nil "Kaikki"
    +valitse-tyyppi+))
