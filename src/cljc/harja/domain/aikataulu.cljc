(ns harja.domain.aikataulu
  "Ylläpitourakan aikataulurivien käsittelyapurit"
  (:require [harja.ui.aikajana :as aikajana]
            [harja.pvm :as pvm]))

(def aikataulujana-tyylit
  {:kohde {::aikajana/reuna "black"}
   :paallystys {::aikajana/vari "#282B2A"}
   :tiemerkinta {::aikajana/vari "#DECB03"}})

(defn aikataulurivi-jana
  "Muuntaa aikataulurivin aikajankomponentin rivimuotoon."
  ([rivi]
   (aikataulurivi-jana (constantly false) (constantly false) rivi))
  ([voi-muokata-paallystys? voi-muokata-tiemerkinta?
     {:keys [aikataulu-kohde-alku aikataulu-kohde-valmis
             aikataulu-paallystys-alku aikataulu-paallystys-loppu
             aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-loppu
             nimi id] :as rivi}]
   {::aikajana/otsikko nimi
    ::aikajana/ajat
    (into []
          (remove nil?)
          [(when (and aikataulu-kohde-alku aikataulu-kohde-valmis)
             (merge (aikataulujana-tyylit :kohde)
                    {::aikajana/drag (when (voi-muokata-paallystys? rivi)
                                       [id :kohde])
                     ::aikajana/alku aikataulu-kohde-alku
                     ::aikajana/loppu aikataulu-kohde-valmis
                     ::aikajana/teksti (str "Koko kohde: "
                                            (pvm/pvm aikataulu-kohde-alku) " \u2013 "
                                            (pvm/pvm aikataulu-kohde-valmis))}))
           (when (and aikataulu-paallystys-alku aikataulu-paallystys-loppu)
             (merge (aikataulujana-tyylit :paallystys)
                    {::aikajana/drag (when (voi-muokata-paallystys? rivi)
                                       [id :paallystys])
                     ::aikajana/alku aikataulu-paallystys-alku
                     ::aikajana/loppu aikataulu-paallystys-loppu
                     ::aikajana/teksti (str "Päällystys: "
                                            (pvm/pvm aikataulu-paallystys-alku) " \u2013 "
                                            (pvm/pvm aikataulu-paallystys-loppu))}))
           (when (and aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-loppu)
             (merge (aikataulujana-tyylit :tiemerkinta)
                    {::aikajana/drag (when (voi-muokata-tiemerkinta? rivi)
                                       [id :tiemerkinta])
                     ::aikajana/alku aikataulu-tiemerkinta-alku
                     ::aikajana/loppu aikataulu-tiemerkinta-loppu
                     ::aikajana/teksti (str "Tiemerkintä: "
                                            (pvm/pvm aikataulu-tiemerkinta-alku) " \u2013 "
                                            (pvm/pvm aikataulu-tiemerkinta-loppu))}))])}))

(defn raahauksessa-paivitetyt-aikataulurivit
  "Palauttaa drag operaation perusteella päivitetyt aikataulurivit tallennusta varten"
  [aikataulurivit {drag ::drag alku ::alku loppu ::loppu}]
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
