(ns harja.palvelin.ajastetut-tehtavat.urakan-tyotuntimuistutukset
  "Tekee ajastetun yhteysvarmistuksen API:n"
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.palvelut.viestinta :as viestinta]
            [harja.domain.urakan-tyotunnit :as ut]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakan-tyotunnit :as q]
            [harja.tyokalut.html :refer [sanitoi]]
            [hiccup.core :refer [html h]]
            [harja.fmt :as fmt]))

(defn laheta-muistutukset-urakoille [fim email urakat vuosi kolmannes kuluvan-kolmanneksen-paattymispaiva]
  (doseq [{:keys [id sampoid hallintayksikko nimi]} urakat]
    (log/info (format "Lähetetään muistutus urakan työtuntien kirjaamisesta urakalle %s (id: %s)" nimi id))
    (let [paattymispvm (fmt/pvm (pvm/dateksi kuluvan-kolmanneksen-paattymispaiva))
          kuukausivali (cond
                         (= 1 kolmannes) "tammikuu - huhtikuu"
                         (= 2 kolmannes) "toukokuu - elokuu"
                         (= 3 kolmannes) "syyskuu - joulukuu"
                         :else "")
          url (format "https://extranet.vayla.fi/harja/#urakat/yleiset?&hy=%s&u=%s"
                      hallintayksikko
                      id)
          otsikko (format "Urakan '%s' työtunnit välille %s kirjaamatta"
                          nimi
                          kuukausivali)
          sisalto (format "Urakan <a href=%s>%s</a> työtunnit vuoden <b>%s</b> välille <b>%s</b> on kirjaamatta.
                           Työtunnit täytyy kirjata <b>%s</b> mennessä."
                          url
                          (sanitoi nimi)
                          (sanitoi vuosi)
                          (sanitoi kuukausivali)
                          (sanitoi paattymispvm))
          viesti {:fim fim
                  :email email
                  :urakka-sampoid sampoid
                  :fim-kayttajaroolit #{"ely urakanvalvoja" "urakan vastuuhenkilö"}
                  :viesti-otsikko otsikko
                  :viesti-body sisalto}]
      (viestinta/laheta-sposti-fim-kayttajarooleille viesti))))

(defn urakan-tyotuntimuistutukset [{:keys [fim sonja-sahkoposti db]} paivittainen-ajoaika]
  (log/info "Ajastetaan muistutukset urakan työtunneista ajettavaksi joka päivä " paivittainen-ajoaika)
  (ajastettu-tehtava/ajasta-paivittain
    paivittainen-ajoaika
    (fn [_]
      (let [kuluva-kolmannes (ut/kuluva-vuosikolmannes)
            vuosi (::ut/vuosi kuluva-kolmannes)
            kolmannes (::ut/vuosikolmannes kuluva-kolmannes)
            kuluvan-kolmanneksen-paattymispaiva (ut/kuluvan-vuosikolmanneksen-paattymispaiva)
            paivia-kolmanneksen-paattymiseen (pvm/paivia-valissa (t/now) kuluvan-kolmanneksen-paattymispaiva)]
        (when (= 3 paivia-kolmanneksen-paattymiseen)
          (let [tunnittomat-urakat (q/hae-urakat-joilla-puuttuu-kolmanneksen-tunnit
                                     db
                                     {:vuosi vuosi
                                      :vuosikolmannes kolmannes})]
            (laheta-muistutukset-urakoille
              fim
              sonja-sahkoposti
              tunnittomat-urakat
              vuosi
              kolmannes
              kuluvan-kolmanneksen-paattymispaiva)))))))

(defrecord UrakanTyotuntiMuistutukset [paivittainen-ajoaika]
  component/Lifecycle
  (start [this]
    (assoc this :urakan-tyotuntimuistutukset (urakan-tyotuntimuistutukset this paivittainen-ajoaika)))
  (stop [this]
    (let [lopeta (get this :urakan-tyotuntimuistutukset)]
      (when lopeta (lopeta)))
    this))
