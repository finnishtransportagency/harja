(ns harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset
  "Muistuttaa tarvittaessa urakoitsijoita lupauksista."
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.lupaus.lupaus-muistutus :as lupaus-muistutus]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.pvm :as pvm]))

(comment
  ;; Funktion kutsuminen REPListä
  (try
    (let [{:keys [db api-sahkoposti fim]} harja.palvelin.main/harja-jarjestelma
          nykyhetki (harja.pvm/luo-pvm 2021 10 1)]
      (harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset/muistuta-lupauksista db fim api-sahkoposti {:nykyhetki nykyhetki}))
    (catch Exception e
      (taoensso.timbre/error e))))

(defn hae-kaynnissa-olevat-urakat
  "Hae ei-poistetut teiden-hoito -tyyppiset urakat, joiden alkuvuosi on annettu alkuvuosi.
  Urakan täytyy olla käynnissä annettuna hetkenä, tai päättynyt korkeintaan 2 kk sitten."
  [db nyt urakan-alkuvuosi]
  (lupaus-kyselyt/hae-kaynnissa-olevat-lupaus-urakat
    db
    {:nykyhetki nyt
     :alkupvm (pvm/->pvm (str "01.10." urakan-alkuvuosi))}))

(defn hae-urakan-lupaustiedot [db {:keys [urakan-alkuvuosi] :as tiedot}]
  (if (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
    (lupaus-palvelu/hae-kuukausittaiset-pisteet-hoitokaudelle db tiedot)
    (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db tiedot)))

(defn hae-muistutettavat-urakat [db nykyhetki urakan-alkuvuosi]
  (let [urakat (hae-kaynnissa-olevat-urakat db nykyhetki urakan-alkuvuosi)
        ;; Päätellään nykyhetkestä kuluva hoitokausi
        hoitokausi (pvm/paivamaaran-hoitokausi nykyhetki)]
    (log/info (format
                "Löydettiin %s kpl vuonna %s alkaneita urakoita, jotka ovat käynnissä."
                (count urakat) urakan-alkuvuosi))
    (doall
      (for [urakka urakat
            :let [tiedot {:urakka-id (:id urakka)
                          :urakan-alkuvuosi urakan-alkuvuosi
                          :nykyhetki nykyhetki
                          :valittu-hoitokausi hoitokausi}
                  urakan-lupaustiedot (hae-urakan-lupaustiedot db tiedot)
                  odottaa-kannanottoa (get-in urakan-lupaustiedot [:yhteenveto :odottaa-kannanottoa])
                  odottaa-urakoitsijan-kannanottoa? (get-in urakan-lupaustiedot [:yhteenveto :odottaa-urakoitsijan-kannanottoa?])]
            :when odottaa-urakoitsijan-kannanottoa?]
        {:urakka urakka
         :odottaa-kannanottoa odottaa-kannanottoa}))))

(defn muistuta-lupauksista
  [db fim email {:keys [nykyhetki] :or {nykyhetki (pvm/nyt)}}]
  (log/info "Kuukauden ensimmäinen päivä toistuva lupauksista muistuttaminen alkaa -> ")
  (doseq [alkuvuosi [2019 2020 2021]]
    (let [urakat (hae-muistutettavat-urakat db nykyhetki alkuvuosi)]
      (log/info (format
                  "Löydettiin %s kpl vuonna %s alkaneita urakoita, joille muistutus täytyy lähettää."
                  (count urakat) alkuvuosi))
      (doseq [{:keys [urakka odottaa-kannanottoa]} urakat]
        (lupaus-muistutus/laheta-muistutus-urakalle fim email urakka alkuvuosi
          odottaa-kannanottoa)))))

(defn kuukauden-ensimmainen? [nykyhetki]
  (= (pvm/paiva nykyhetki) 1))

(defn muistutustehtava
  "Jos on kuukauden ensimmäinen päivä, niin muistuta lupauksista tarvittaessa."
  [db fim email nykyhetki]
  ;; Varmistetaan, että muistutus lähetetään vain kerran kuukaudessa ja vain ensimmäisenä päivänä
  (when (kuukauden-ensimmainen? nykyhetki)
    (log/debug "On kuukauden ensimmäinen päivä -> ajetaan lupausmuistutukset.")
    (lukko/yrita-ajaa-lukon-kanssa db "lupaus-muistutukset"
      #(muistuta-lupauksista db fim email {:nykyhetki nykyhetki}))))

(defn ajastus [db fim email]
  "Ajastetaan muistutukset urakan lupauksista ajettavaksi vain kuukauden ensimmäinen päivä."
  (ajastettu-tehtava/ajasta-paivittain
    [10 00 0] ; VHAR-5523: lähetetään virka-aikaan, jotta Destian päivystäjä ei herää turhaan
    (do
      (log/info "ajasta-paivittain :: muistutus urakan lupauksista :: Alkaa " (pvm/nyt))
      (fn [_]
        (muistutustehtava db fim email (pvm/nyt))))))

(defrecord UrakanLupausMuistutukset []
  component/Lifecycle
  (start [{:keys [db fim api-sahkoposti] :as this}]
    (assoc this :urakan-lupausmuistutukset (ajastus db fim api-sahkoposti)))
  (stop [this]
    (let [lopeta (get this :urakan-lupausmuistutukset)]
      (when lopeta (lopeta)))
    this))
