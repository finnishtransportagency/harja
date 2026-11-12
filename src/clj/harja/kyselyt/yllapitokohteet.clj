(ns harja.kyselyt.yllapitokohteet
  (:require [jeesql.core :refer [defqueries]]
            [harja.geo :as geo]
            [jeesql.postgres :as postgres]
            [harja.kyselyt.konversio :as konv]
            [clojure.set :as set]))

(defqueries "harja/kyselyt/yllapitokohteet.sql"
  ;; PENDING: ylläpitokohteen poiston päättely on edelleen melko hidas.
  ;; Nyt tehdään yksi kysely, joka hakee kaikki urakan kohteiden linkitykset.
  ;; Jätetään tämä jatkoa varten, ota raportointi käyttöön testauksessa.
  #_{:report-slow-queries postgres/report-slow-queries})

(declare paivita-yllapitokohteen-paallysteen-korjausluokka hae-yllapitokohteen-aikataulu
  paivita-yllapitokohteen-paallystysaikataulu! luo-yllapitokohteelle-tyhja-aikataulu<!
  paivita-yllapitokohde<! luo-yllapitokohde<! hae-urakan-tyyppi
  paivita-yllapitokohteen-korjausluokat-ja-yllapitoluokat paivita-yllapitokohteen-kohdeosien-sijainnit!
  hae-ilman-sijaintia-olevat-yllapitokohdeosat luo-yllapitokohteelle-tyhja-kustannustaulu<! luo-yllapitokohdeosa<!
  poista-yllapitokohdeosa! paallystyskohteen-saa-poistaa
  hae-urakan-yllapitokohteiden-yllapitokohdeosat-alueelle hae-urakan-yllapitokohteiden-yllapitokohdeosat
  hae-yllapitokohteen-suorittava-tiemerkintaurakka-id hae-yllapitokohteen-urakka-id hae-paikkauskohteen-urakka-id
  yllapitokohde-sisaltaa-kirjauksia-urakassa hae-yllapitokohteen-tiemerkintaaikataulu
  yllapitokohteen-saa-poistaa hae-urakan-sopimuksen-yllapitokohteet yllapitokohteet-joille-linkityksia
  hae-yllapitokohteiden-aikataulun-muokkaus-aika paivita-paallystys-tai-paikkausurakan-geometria
  tallenna-tiemerkintakohteen-aikataulu! tallenna-yllapitokohteen-valmis-viimeistaan-tiemerkintaurakasta!
  poista-valmistuneen-tiemerkinnan-sahkopostitiedot! tallenna-valmistuneen-tiemerkkinnan-sahkopostitiedot<!
  hae-tiemerkintaurakan-aikataulu hae-paallystysurakan-aikataulu hae-tiemerkintaurakalle-osoitetut-yllapitokohteet
  hae-urakan-yllapitokohteet-lomakkeelle hae-urakan-yllapitokohteen-yllapitokohdeosat
  hae-tiemerkinnan-suorittavat-urakat hae-kohteen-merkinta-ja-jyrsintatiedot merkitse-kohde-valmiiksi-tiemerkintaan<!
  tallenna-paallystyskohteen-aikataulu! tallenna-yllapitokohteen-suorittava-tiemerkintaurakka!
  tallenna-yllapitokohteen-kustannukset-yhaid! onko-olemassa-urakalla? hae-urakkaan-liittyvat-tiemerkintakohteet
  hae-urakkaan-kuuluvat-yllapitokohteet hae-yllapitokohde-urakan-nimella paivita-yllapitokohteen-tiemerkintaaikataulu!
  paivita-yllapitokohteen-paallystysilmoituksen-aikataulu<! hae-kaikki-urakan-yllapitokohteet
  hae-paikkauskohteen-paikkaukset-alueelle hae-paikkauskohteen-paikkaukset
  hae-yllapitokohteiden-tiedot-sahkopostilahetykseen)

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn liita-kohdeosat-kohteisiin
  ([db kohteet kohde-id-avain]
   (liita-kohdeosat-kohteisiin db kohteet kohde-id-avain nil))
  ([db kohteet kohde-id-avain {:keys [alue toleranssi]}]
   (let [idt (map kohde-id-avain kohteet)
         kohdeosat (into []
                         kohdeosa-xf
                         (if alue
                           (hae-urakan-yllapitokohteiden-yllapitokohdeosat-alueelle
                            db (merge alue {:idt idt :toleranssi toleranssi}))
                           (hae-urakan-yllapitokohteiden-yllapitokohdeosat
                            db {:idt idt})))]
     (mapv
      (fn [kohde]
        (let [kohteen-kohdeosat (filterv #(= (:yllapitokohde-id %) (kohde-id-avain kohde)) kohdeosat)]
          (assoc kohde :kohdeosat kohteen-kohdeosat)))
      kohteet))))

(defn liita-paikkaukset-paikkauskohteisiin
  [db kohteet kohde-id-avain {:keys [alue toleranssi alkupvm loppupvm]}]
  (let [idt (map kohde-id-avain kohteet)
        kohdeosat (into []
                        kohdeosa-xf
                        (if alue
                          (hae-paikkauskohteen-paikkaukset-alueelle
                            db (merge alue {:idt idt :toleranssi toleranssi
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm}))
                          (hae-paikkauskohteen-paikkaukset
                            db {:idt idt
                                :alkupvm alkupvm
                                :loppupvm loppupvm})))]
    (mapv
      (fn [kohde]
        (let [kohteen-kohdeosat (filterv #(= (:yllapitokohde-id %) (kohde-id-avain kohde)) kohdeosat)]
          (assoc kohde :kohdeosat kohteen-kohdeosat)))
      kohteet)))

(def muunna-urakka-vastaanottajat
  (comp (fn [rivi]
          (update rivi :sahkopostitiedot_urakka-vastaanottajat
                  (fn [uv-lista]
                    (mapv #(if %
                             (konv/pgobject->map % :urakka_id :long :email :string)
                             %)
                          uv-lista))))
        #(konv/array->vec % :sahkopostitiedot_urakka-vastaanottajat)))

(defn yllapitokohteiden-tiedot-sahkopostilahetykseen [db kohde-idt]
  (let [tiedot (into []
                     (comp
                       (map muunna-urakka-vastaanottajat)
                       (map #(konv/array->set % :sahkopostitiedot_muut-vastaanottajat))
                       (map konv/alaviiva->rakenne))
                     (hae-yllapitokohteiden-tiedot-sahkopostilahetykseen db {:idt kohde-idt}))]
    tiedot))
