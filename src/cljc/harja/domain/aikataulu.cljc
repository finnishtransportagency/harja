(ns harja.domain.aikataulu
  "Ylläpitourakan aikataulurivien käsittelyapurit"
  (:require [harja.ui.aikajana :as aikajana]
            [harja.pvm :as pvm]))

(def aikataulujana-tyylit
  {:kohde {::aikajana/reuna "black"}
   :paallystys {::aikajana/vari "#282B2A"}
   :tiemerkinta {::aikajana/vari "#DECB03"}})

(defn- aikajana-teksti [nimi alkupvm loppupvm]
  (str nimi ": "
       (cond
         (and alkupvm loppupvm) (str (pvm/pvm alkupvm) " \u2013 "
                                     (pvm/pvm loppupvm))
         alkupvm (str "aloitus " (pvm/pvm alkupvm))
         loppupvm (str "lopetus " (pvm/pvm loppupvm))
         :default nil)))

(defn aikataulurivi-jana
  "Muuntaa aikataulurivin aikajankomponentin rivimuotoon."
  ([rivi]
   (aikataulurivi-jana (constantly false) (constantly false) rivi))
  ([voi-muokata-paallystys? voi-muokata-tiemerkinta?
    {:keys [aikataulu-kohde-alku aikataulu-kohde-valmis
            aikataulu-paallystys-alku aikataulu-paallystys-loppu
            aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-loppu
            nimi id kohdenumero] :as rivi}]
   {::aikajana/otsikko (str kohdenumero " - " nimi)
    ::aikajana/ajat
    (into []
          (remove nil?)
          [(when (or aikataulu-kohde-alku aikataulu-kohde-valmis)
             (merge (aikataulujana-tyylit :kohde)
                    {::aikajana/drag (when (voi-muokata-paallystys? rivi)
                                       [id :kohde])
                     ::aikajana/alku aikataulu-kohde-alku
                     ::aikajana/loppu aikataulu-kohde-valmis
                     ::aikajana/teksti (aikajana-teksti "Koko kohde"
                                                        aikataulu-kohde-alku
                                                        aikataulu-kohde-valmis)}))
           (when (or aikataulu-paallystys-alku aikataulu-paallystys-loppu)
             (merge (aikataulujana-tyylit :paallystys)
                    {::aikajana/drag (when (voi-muokata-paallystys? rivi)
                                       [id :paallystys])
                     ::aikajana/alku aikataulu-paallystys-alku
                     ::aikajana/loppu aikataulu-paallystys-loppu
                     ::aikajana/teksti (aikajana-teksti "Päällystys"
                                                        aikataulu-paallystys-alku
                                                        aikataulu-paallystys-loppu)}))
           (when (or aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-loppu)
             (merge (aikataulujana-tyylit :tiemerkinta)
                    {::aikajana/drag (when (voi-muokata-tiemerkinta? rivi)
                                       [id :tiemerkinta])
                     ::aikajana/alku aikataulu-tiemerkinta-alku
                     ::aikajana/loppu aikataulu-tiemerkinta-loppu
                     ::aikajana/teksti (aikajana-teksti "Tiemerkintä"
                                                        aikataulu-tiemerkinta-alku
                                                        aikataulu-tiemerkinta-loppu)}))])}))

(defn raahauksessa-paivitetyt-aikataulurivit
  "Palauttaa drag operaation perusteella päivitetyt aikataulurivit tallennusta varten"
  [aikataulurivit {drag ::aikajana/drag alku ::aikajana/alku loppu ::aikajana/loppu}]
  (keep
    (fn [{id :id :as aikataulurivi}]
      (when (= id (first drag))
        (let [[alku-avain loppu-avain]
              (case (second drag)
                :kohde [:aikataulu-kohde-alku :aikataulu-kohde-valmis]
                :paallystys [:aikataulu-paallystys-alku :aikataulu-paallystys-loppu]
                :tiemerkinta [:aikataulu-tiemerkinta-alku :aikataulu-tiemerkinta-loppu])]
          (assoc aikataulurivi
            alku-avain alku
            loppu-avain loppu))))
    aikataulurivit))

(defn aikataulun-alku-ja-loppu-validi?
  "Tarkistaa että aikajanan päällystystoimenpiteen uusi päivämäärävalinta on validi.
  Toimenpide ei saa alkaa ennen kohteen aloitusta, eikä loppua kohteen lopetuksen jälkeen.
  Ei tarkista tiemerkintään liittyviä rajoituksia."
  [aikataulurivit {drag ::aikajana/drag alku ::aikajana/alku loppu ::aikajana/loppu}]
  (first (keep
          (fn [{id :id :as aikataulurivi}]
            (if (and (= id (first drag)) (= :paallystys (second drag)))
               (let [kohde-alku (get aikataulurivi :aikataulu-kohde-alku)
                    kohde-loppu (get aikataulurivi :aikataulu-kohde-valmis)]
                (and (pvm/sama-tai-jalkeen? alku kohde-alku)
                     (pvm/sama-tai-ennen? loppu kohde-loppu)))
              true))
          aikataulurivit)))