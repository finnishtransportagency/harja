(ns harja.palvelin.ajastetut-tehtavat.reittien-validointi
  "Namespace, joka päivittäin hakee toteumat, joilla ei ole reittiä, mutta on reittipisteet tai tr-osoite, ja
  yrittää ajaa näille reitit uusiksi.

  Tällaisia toteumia on päässyt syntymään tuotantoon. Tämän namespacen voi poistaa, kun tällaisia
  toteumia ei enää synny."
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.reittitoteuma :as reittitoteuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastus]
            [harja.palvelin.palvelut.tierekisteri-haku :as tierekisteri]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.palvelin.palvelut.toteumat :refer [paivita-toteuman-reitti]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [com.stuartsierra.component :as component]))

(def maksimi-etaisyys reittitoteuma/maksimi-linnuntien-etaisyys)

(defn- paivita-reittipisteelliset-toteumat
  "Hakee toteumat joille on reittipisteitä, mutta ei reittiä, ja yrittää muodostaa reitin uusiksi."
  [db]
  (log/info "Aloitetaan reittien luonti reittipisteellisille toteumille.")
  (lukko/yrita-ajaa-lukon-kanssa db "reittipiste-reitti-paivitys"
    #(loop [etaisyys reittitoteuma/maksimi-linnuntien-etaisyys]
     (let [toteumat (toteumat-q/hae-reitittomat-mutta-reittipisteelliset-toteumat db)]
       (when-not (empty? toteumat)
         (log/info (format "Löydettiin %s reittipisteellistä toteumaa, jolta reitti puuttuu. Yritetään laskea reitti uusiksi."
                            (count toteumat)))
         (jdbc/with-db-transaction
           [db db]
           (doseq [{:keys [id]} toteumat]
             (reittitoteuma/paivita-toteuman-reitti db id etaisyys)))
         (when-not (<= maksimi-etaisyys etaisyys)
           (recur (+ etaisyys 200))))))))

(defn- paivita-osoitteelliset-toteumat
  "Hakee toteumat, joille on tr-osoite, mutta ei reittiä, ja yrittää muodostaa reitin uusiksi."
  [db]
  (log/info "Aloitetaan reittien luonti tr-osoitteellisille toteumille.")
  (lukko/yrita-ajaa-lukon-kanssa db "tr-reitti-paivitys"
    #(let [toteumat (toteumat-q/hae-reitittomat-mutta-osoitteelliset-toteumat db)]
     (when-not (empty? toteumat)
       (log/info (format "Löydettiin %s toteumaa jolla on tr-osoite, mutta ei reittigeometriaa. Yritetään laskea reitti." (count toteumat)))
       (jdbc/with-db-transaction
         [db db]
         ;; {:id, :numero, :alkuosa; :alkuetaisyys, :loppuosa, :loppuetaisyys}
         (doseq [tiedot toteumat]
           (let [reitti (tierekisteri/hae-tr-viiva db tiedot)]
             (when-not (:virhe reitti)
               (paivita-toteuman-reitti db (:id tiedot) (first reitti))))))))))

(defn tee-toteumien-reittien-tarkistustehtava
  [{:keys [db]} paivittainen-aika]
  (when (and (ominaisuus-kaytossa? :reittitarkistukset) paivittainen-aika)
    (ajastus/ajasta-paivittain
      paivittainen-aika
      (fn [_]
        (paivita-reittipisteelliset-toteumat db)
        (paivita-osoitteelliset-toteumat db)))))

(defrecord Reittitarkistukset [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this
      :reittien-tarkistus (tee-toteumien-reittien-tarkistustehtava this (:paivittainen-aika asetukset))))
  (stop [this]
    (doseq [tehtava [:reittien-tarkistus]
            :let [lopeta-fn (get this tehtava)]]
      (when lopeta-fn (lopeta-fn)))
    this))
