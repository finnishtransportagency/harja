(ns harja.palvelin.api.urakat
  "Urakan yleistietojen API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.api.yleinen :refer [virhe vastaus]]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.kyselyt.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.palvelin.api.skeemat :as skeemat]))

(defn muodosta-kokonaishintaiset-tyot [tyot]
  (for [{:keys [id vuosi kuukausi summa tpi_id tpi_nimi] :as tyo} tyot]
    {:kokonaishintainenTyo
     {:id       id
      :tehtavat [{:tehtava {:id tpi_id :selite tpi_nimi}}]
      :vuosi    vuosi
      :kuukausi kuukausi
      :summa    summa}}))

(defn muodosta-yksikkohintaiset-tyot [tyot]
  (for [{:keys [id alkupvm loppupvm maara yksikko yksikkohinta tehtavan_id tehtavan_nimi] :as tyo} tyot]
    {:yksikkohintainenTyo
     {:id           id
      :tehtava      {:id tehtavan_id :selite tehtavan_nimi} ;; 4. tason tehtava
      :alkupvm      alkupvm
      :loppupvm     loppupvm
      :maara        maara
      :yksikko      yksikko
      :yksikkohinta yksikkohinta}}))

(defn muodosta-materiaalin-kaytot [materiaalit]
  (for [{:keys [id alkupvm loppupvm maara materiaali] :as mat} (map konv/alaviiva->rakenne materiaalit)]
    {:materiaalinKaytto
     {:maara    {:materiaali
                 ;; FIXME: Tämä lista pitää tarkistaa Annelta, materiaali UI:n puolella on kommentoitu,
                 ;; että vain suolankäyttö kiinnostaa... turha naulata tätä listaa APIin kiinni, jos sen
                 ;; on asiakas jo huonoksi todennut.
                          (case (:nimi materiaali)
                            "Talvisuolaliuos NaCl" "talvisuolaliuosNaCl"
                            "Talvisuolaliuos CaCl2" "talvisuolaliuosCaCl2"
                            "Erityisalueet NaCl" "erityisalueetNaCl"
                            "Erityisalueet NaCl-liuos" "erityisalueetNaClLiuos"
                            "Hiekoitushiekka" "hiekoitushiekka"
                            "Kaliumformiaatti" "kaliumformiaatti"

                            ;; default
                            "Talvisuolaliuos NaCl")
                 :yksikko (:yksikko materiaali)
                 :maara   maara}
      :alkupvm  alkupvm
      :loppupvm loppupvm}}))

(defn hae-urakan-sopimukset [db urakka-id]
  (let [sopimukset (urakat/hae-urakan-sopimukset db urakka-id)
        kokonaishintaiset (group-by :sopimus (kokonaishintaiset-tyot/listaa-kokonaishintaiset-tyot db urakka-id))
        yksikkohintaiset (group-by :sopimus (yksikkohintaiset-tyot/listaa-urakan-yksikkohintaiset-tyot db urakka-id))
        materiaalit (group-by :sopimus (materiaalit/hae-urakan-materiaalit db urakka-id))]
    (for [sopimus sopimukset]
      {:sopimus (assoc sopimus
                  :kokonaishintaisetTyot (muodosta-kokonaishintaiset-tyot (get kokonaishintaiset (:id sopimus)))
                  :yksikkohintaisetTyot (muodosta-yksikkohintaiset-tyot (get yksikkohintaiset (:id sopimus)))
                  :materiaalinKaytot (muodosta-materiaalin-kaytot (get materiaalit (:id sopimus))))})))

(defn rakenna-vastaus [db id urakka]
  {:urakka                                                  ;; URAKAN tiedot
   {:tiedot     (assoc urakka                               ; perustiedot (pl. väylämuoto) tulevat suoraan hae-urakka kyselystä
                  :vaylamuoto "tie")
    :sopimukset (hae-urakan-sopimukset db id)}})

(defn hae-urakka [db urakka-id]
  ;; FIXME: mieti mekanismi urakoiden pääsynvalvontaan
  (try
    (let [id (Integer/parseInt urakka-id)
          urakka (some->> id (urakat/hae-urakka db) first
                          konv/alaviiva->rakenne)]
      (if-not urakka
        ;; Jos urakkaa ei löydy, palautetaan virhe
        (virhe "Tuntematon urakka"                          ;; FIXME: api virheet constanteina
               (str "Urakkaa id:llä " urakka-id " ei löydy."))
        (vastaus
          skeemat/+urakan-haku-vastaus+
          (rakenna-vastaus db id urakka))))
    (catch Exception e
      (log/warn e "Urakan haku epäonnistui.")
      (virhe "Sisäinen käsittelyvirhe" (.getMessage e)))))


(defrecord Urakat []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-reitti
      http :api-hae-urakka
      (GET "/api/urakat/:id" [id]
        (hae-urakka db id)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :api-hae-urakka)
    this))


  

