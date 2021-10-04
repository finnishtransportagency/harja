(ns harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset
  "Muistuttaa tarvittaessa urakoitsijoita lupauksista."
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.lupaus.lupaus-muistutus :as lupaus-muistutus]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]))

(comment
  ;; Funktion kutsuminen REPListä
  (let [{:keys [db sonja-sahkoposti fim]} harja.palvelin.main/harja-jarjestelma
        nykyhetki (harja.pvm/luo-pvm 2021 10 1)]
    (harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset/muistuta-lupauksista db fim sonja-sahkoposti {:nykyhetki nykyhetki})))

(defn hae-kaynnissa-olevat-urakat
  "Hae ei-poistetut teiden-hoito -tyyppiset urakat, joiden alkuvuosi on annettu alkuvuosi.
  Urakan täytyy olla käynnissä annettuna hetkenä, tai päättynyt korkeintaan 2 kk sitten."
  [db nyt urakan-alkuvuosi]
  (lupaus-kyselyt/hae-kaynnissa-olevat-lupaus-urakat
    db
    {:nykyhetki nyt
     :alkupvm (pvm/->pvm (str "01.10." urakan-alkuvuosi))}))

(defn hae-muistutettavat-urakat [db nykyhetki urakan-alkuvuosi]
  (let [urakat (hae-kaynnissa-olevat-urakat db nykyhetki urakan-alkuvuosi)
        ;; Päätellään nykyhetkestä kuluva hoitokausi
        hoitokausi (pvm/paivamaaran-hoitokausi nykyhetki)]
    (log/info "Löydettiin" (count urakat) "kpl käynnissä olevia urakoita, joiden alkuvuosi on" urakan-alkuvuosi ".")
    (doall
      (for [urakka urakat
            :let [tiedot {:urakka-id (:id urakka)
                          :urakan-alkuvuosi urakan-alkuvuosi
                          :nykyhetki nykyhetki
                          :valittu-hoitokausi hoitokausi}
                  urakan-lupaustiedot (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db tiedot)
                  odottaa-kannanottoa (get-in urakan-lupaustiedot [:yhteenveto :odottaa-kannanottoa])
                  merkitsevat-odottaa-kannanottoa (get-in urakan-lupaustiedot [:yhteenveto :merkitsevat-odottaa-kannanottoa])
                  urakoitsija-kiinnostunut-muistutuksesta? (> odottaa-kannanottoa merkitsevat-odottaa-kannanottoa)]
            :when urakoitsija-kiinnostunut-muistutuksesta?]
        {:urakka urakka
         :odottaa-kannanottoa odottaa-kannanottoa}))))

(defn muistuta-lupauksista
  "Ajetaan vain jos on kuukauden ensimmäinen päivä.
  Testausta varten voidaan antaa nykyhetki."
  [db fim sonja-sahkoposti {:keys [nykyhetki] :or {nykyhetki (pvm/nyt)}}]
  (log/info "Kuukauden ensimmäinen päivä toistuva lupauksista muistuttaminen alkaa -> ")
  (doseq [alkuvuosi [2019 2020 2021]]
    (let [urakat (hae-muistutettavat-urakat db nykyhetki alkuvuosi)]
      (log/info (format
                  "Löydettiin %s kpl vuonna %s alkaneita urakoita, joille muistuts täytyy lähettää."
                  (count urakat) alkuvuosi))
      (doseq [{:keys [urakka odottaa-kannanottoa]} urakat]
        (lupaus-muistutus/laheta-muistutus-urakalle fim sonja-sahkoposti urakka alkuvuosi
          odottaa-kannanottoa)))))

(defn ajastus [db fim sonja-sahkoposti]
  "Ajastetaan muistutukset urakan lupauksista ajettavaksi vain kuukauden ensimmäinen päivä."
  (ajastettu-tehtava/ajasta-paivittain
    [1 50 0] ; Yöllä klo 01:50:00
    (fn [_]
      (let [onko-kuukauden-ensimmainen? (= (pvm/paiva (pvm/nyt)) 1)]
        ;; Varmistetaan, että muistutus lähetetään vain kerran kuukaudessa ja vain ensimmäisenä päivänä
        (lukko/yrita-ajaa-lukon-kanssa db "lupaus-muistutukset"
                                       #(when onko-kuukauden-ensimmainen?
                                          (muistuta-lupauksista db fim sonja-sahkoposti {})))))))

(defrecord UrakanLupausMuistutukset []
  component/Lifecycle
  (start [{:keys [db fim sonja-sahkoposti] :as this}]
    (assoc this :urakan-lupausmuistutukset (ajastus db fim sonja-sahkoposti)))
  (stop [this]
    (let [lopeta (get this :urakan-lupausmuistutukset)]
      (when lopeta (lopeta)))
    this))
