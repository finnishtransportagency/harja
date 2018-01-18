(ns harja.domain.aikataulu
  "Ylläpitourakan aikataulurivien käsittelyapurit"
  (:require [harja.ui.aikajana :as aikajana]
            [harja.domain.yllapitokohde :as ypk]
            [harja.pvm :as pvm]))

(def aikataulujana-tyylit
  {:kohde {::aikajana/reuna "black"}
   :paallystys {::aikajana/vari "#282B2A"}
   :tiemerkinta {::aikajana/vari "#DECB03"}
   :muu {::aikajana/vari "#03a9de"}})

(def tarkka-aikataulujana-tyylit
  {:rp_tyot {::aikajana/vari "#0384ac"}
   :ojankaivuu {::aikajana/vari "#0390bd"}
   :rumpujen_vaihto {::aikajana/vari "#039bcc"}
   :oletus {::aikajana/vari "#03a9de"}})

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
   (aikataulurivi-jana rivi {}))
  ([{:keys [aikataulu-kohde-alku aikataulu-kohde-valmis
            aikataulu-paallystys-alku aikataulu-paallystys-loppu
            aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-loppu
            nimi id kohdenumero] :as rivi}
    {:keys [voi-muokata-paallystys? voi-muokata-tiemerkinta?
            nayta-tarkka-aikajana?] :as tiedot}]
   (let [yllapitokohde-id id
         voi-muokata-paallystys? (or voi-muokata-paallystys? (constantly false))
         voi-muokata-tiemerkinta? (or voi-muokata-tiemerkinta? (constantly false))]
     {::aikajana/otsikko (str kohdenumero " - " nimi)
      ::aikajana/ajat
      ;; Ylläpitokohteen "perusaikataulu"
      (vec (concat
             (into []
                   (remove nil?)
                   [(when (or aikataulu-kohde-alku aikataulu-kohde-valmis)
                      (merge (aikataulujana-tyylit :kohde)
                             {::aikajana/drag (when (voi-muokata-paallystys? rivi)
                                                [yllapitokohde-id :kohde])
                              ::aikajana/alku aikataulu-kohde-alku
                              ::aikajana/loppu aikataulu-kohde-valmis
                              ::aikajana/teksti (aikajana-teksti "Koko kohde"
                                                                 aikataulu-kohde-alku
                                                                 aikataulu-kohde-valmis)}))
                    (when (or aikataulu-paallystys-alku aikataulu-paallystys-loppu)
                      (merge (aikataulujana-tyylit :paallystys)
                             {::aikajana/drag (when (voi-muokata-paallystys? rivi)
                                                [yllapitokohde-id :paallystys])
                              ::aikajana/alku aikataulu-paallystys-alku
                              ::aikajana/loppu aikataulu-paallystys-loppu
                              ::aikajana/teksti (aikajana-teksti "Päällystys"
                                                                 aikataulu-paallystys-alku
                                                                 aikataulu-paallystys-loppu)}))
                    (when (or aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-loppu)
                      (merge (aikataulujana-tyylit :tiemerkinta)
                             {::aikajana/drag (when (voi-muokata-tiemerkinta? rivi)
                                                [yllapitokohde-id :tiemerkinta])
                              ::aikajana/alku aikataulu-tiemerkinta-alku
                              ::aikajana/loppu aikataulu-tiemerkinta-loppu
                              ::aikajana/teksti (aikajana-teksti "Tiemerkintä"
                                                                 aikataulu-tiemerkinta-alku
                                                                 aikataulu-tiemerkinta-loppu)}))])
             ;; Ylläpitokohteen yksityiskohtainen aikataulu
             (when nayta-tarkka-aikajana?
               (map
                 (fn [{:keys [id toimenpide kuvaus alku loppu] :as drag-tiedot}]
                   (merge (or (tarkka-aikataulujana-tyylit toimenpide)
                              (tarkka-aikataulujana-tyylit :oletus))
                          {::aikajana/drag (when (voi-muokata-paallystys? rivi) ;; TODO Oikeustarkistus sen mukaan kuuluuko palkki mihin urakkaan
                                             [yllapitokohde-id :tarkka-aikataulu id])
                           ::aikajana/alku alku
                           ::aikajana/loppu loppu
                           ::aikajana/teksti (aikajana-teksti
                                               (if (and (= toimenpide :muu) kuvaus)
                                                 kuvaus
                                                 (ypk/tarkan-aikataulun-toimenpide-fmt toimenpide))
                                               alku
                                               loppu)}))
                 (:tarkka-aikataulu rivi)))))})))

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
                :tiemerkinta [:aikataulu-tiemerkinta-alku :aikataulu-tiemerkinta-loppu]
                nil)]
          (if (and alku-avain loppu-avain)
            (assoc aikataulurivi
              alku-avain alku
              loppu-avain loppu)
            aikataulurivi))))
    aikataulurivit))

(defn aikataulu-validi?
  "Tarkistaa että aikajanan päällystystoimenpiteen uusi päivämäärävalinta on validi.
  Päällystys ei saa alkaa ennen kohteen aloitusta, eikä loppua kohteen lopetuksen jälkeen."
  [aikataulurivit {drag ::aikajana/drag alku ::aikajana/alku loppu ::aikajana/loppu}]
  (first (keep
           (fn [{id :id :as aikataulurivi}]
             (cond
               (and (= id (first drag)) (= :paallystys (second drag)))
               (let [kohde-alku (get aikataulurivi :aikataulu-kohde-alku)
                     kohde-loppu (get aikataulurivi :aikataulu-kohde-valmis)]
                 (and (or (nil? kohde-alku) (pvm/sama-tai-jalkeen? alku kohde-alku))
                      (or (nil? kohde-loppu) (pvm/sama-tai-ennen? loppu kohde-loppu))))

               (and (= id (first drag)) (= :kohde (second drag)))
               (let [paallystys-alku (get aikataulurivi :aikataulu-paallystys-alku)
                     paallystys-loppu (get aikataulurivi :aikataulu-paallystys-loppu)]
                 (and (or (nil? paallystys-alku) (pvm/sama-tai-jalkeen? paallystys-alku alku))
                      (or (nil? paallystys-loppu) (pvm/sama-tai-ennen? paallystys-loppu loppu))))

               :default
               true))
           aikataulurivit)))