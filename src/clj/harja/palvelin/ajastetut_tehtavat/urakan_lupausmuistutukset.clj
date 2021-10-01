(ns harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset
  "Muistuttaa tarvittaessa 2021 alkaneissa urakoissa urakoitsijoita lupauksista."
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

(defn hae-muistutettavat-urakat
  "Hae muistutettavat urakat tietokannasta.
  Funktiota kutsutaan testeistä."
  [db nyt urakan-alkuvuosi]
  (lupaus-kyselyt/hae-kaynnissa-olevat-lupaus-urakat
    db
    {:nykyhetki nyt
     :alkupvm (pvm/->pvm (str "01.10." urakan-alkuvuosi))}))

(defn muistuta-lupauksista
  "Ajetaan vain jos on kuukauden ensimmäinen päivä.
  Testausta varten voidaan antaa nykyhetki ja urakan alkuvuosi."
  [db fim sonja-sahkoposti {:keys [nykyhetki urakan-alkuvuosi]}]
  (log/info "Kuukauden ensimmäinen päivä toistuva lupauksista muistuttaminen alkaa -> ")
  (let [nykyhetki (or nykyhetki (pvm/nyt))
        urakan-alkuvuosi (or urakan-alkuvuosi 2021)
        muistutettavat-urakat (hae-muistutettavat-urakat db nykyhetki urakan-alkuvuosi)]
    (log/info "Löydettiin" (count muistutettavat-urakat) "kpl urakoita, joille muistutus täytyy lähettää.")
    (doseq [urakka muistutettavat-urakat]
      (let [;; Päätellään nykyhetkestä kuluva hoitokausi
            hoitokausi (pvm/paivamaaran-hoitokausi nykyhetki)
            tiedot {:urakka-id (:id urakka)
                    :urakan-alkuvuosi urakan-alkuvuosi
                    :nykyhetki nykyhetki
                    :valittu-hoitokausi hoitokausi}
            urakan-lupaustiedot (lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle db tiedot)
            odottaa-kannanottoa (get-in urakan-lupaustiedot [:yhteenveto :odottaa-kannanottoa])
            merkitsevat-odottaa-kannanottoa (get-in urakan-lupaustiedot [:yhteenveto :merkitsevat-odottaa-kannanottoa])
            urakoitsija-kiinnostunut-muistutuksesta? (> odottaa-kannanottoa merkitsevat-odottaa-kannanottoa)]
        (when urakoitsija-kiinnostunut-muistutuksesta?
          (lupaus-muistutus/laheta-muistutus-urakalle fim sonja-sahkoposti urakka odottaa-kannanottoa))))
    muistutettavat-urakat))

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
