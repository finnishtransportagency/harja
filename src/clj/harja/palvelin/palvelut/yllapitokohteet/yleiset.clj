(ns harja.palvelin.palvelut.yllapitokohteet.yleiset
  "Yleisiä apufunktioita ylläpitokohteiden palveluille"
  (:require [harja.kyselyt
             [konversio :as konv]
             [yllapitokohteet :as q]]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [hiccup.core :refer [html]]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.tieverkko :as tieverkko]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
            [harja.domain.tierekisteri :as tr]
            [harja.palvelin.palvelut.tierek-haku :as tr-haku])
  (:use org.httpkit.fake))

(defn tarkista-urakkatyypin-mukainen-kirjoitusoikeus [db user urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "paallystys"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
      "paikkaus"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
      "tiemerkinta"
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id))))

(defn tarkista-urakkatyypin-mukainen-lukuoikeus [db user urakka-id]
  (let [urakan-tyyppi (:tyyppi (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (case urakan-tyyppi
      "paallystys"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
      "paikkaus"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
      "tiemerkinta"
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id))))

(defn vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa [db urakka-id yllapitokohde-id]
  "Tarkistaa, että ylläpitokohde kuuluu annettuun urakkaan tai annettu urakka on merkitty
   suorittavaksi tiemerkintäurakakaksi. Jos kumpikaan ei ole totta, heittää poikkeuksen."
  (assert (and urakka-id yllapitokohde-id) "Ei voida suorittaa tarkastusta")
  (let [kohteen-urakka (:id (first (q/hae-yllapitokohteen-urakka-id db {:id yllapitokohde-id})))
        kohteen-suorittava-tiemerkintaurakka (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                           db
                                                           {:id yllapitokohde-id})))]
    (when (and (not= kohteen-urakka urakka-id)
               (not= kohteen-suorittava-tiemerkintaurakka urakka-id))
      (throw (SecurityException. (str "Ylläpitokohde " yllapitokohde-id " ei kuulu valittuun urakkaan "
                                      urakka-id " vaan urakkaan " kohteen-urakka
                                      ", eikä valittu urakka myöskään ole kohteen suorittava tiemerkintäurakka"))))))

(defn vaadi-yllapitokohde-osoitettu-tiemerkintaurakkaan [db urakka-id yllapitokohde-id]
  "Tarkistaa, että ylläpitokohde on osoitettu annetulle tiemerkintäurakka-id:lle suoritettavaksi.
   Jos ei ole, heittää poikkeuksen."
  (assert (and urakka-id yllapitokohde-id) "Ei voida suorittaa tarkastusta")
  (let [kohteen-suorittava-tiemerkintaurakka (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                           db
                                                           {:id yllapitokohde-id})))]
    (when (not= kohteen-suorittava-tiemerkintaurakka urakka-id)
      (throw (SecurityException. (str "Ylläpitokohde " yllapitokohde-id " ei ole urakan"
                                      urakka-id " suoritettavana tiemerkintään, vaan urakan "
                                      kohteen-suorittava-tiemerkintaurakka))))))

(defn vaadi-yllapitokohde-kuuluu-urakkaan
  [db urakka-id yllapitokohde-id]
  "Tarkistaa, että ylläpitokohde kuuluu annettuun urakkaan. Jos ei kuulu, heittää poikkeuksen."
  (assert (and urakka-id yllapitokohde-id) "Ei voida suorittaa tarkastusta")
  (let [kohteen-urakka (:id (first (q/hae-yllapitokohteen-urakka-id db {:id yllapitokohde-id})))]
    (when (not= kohteen-urakka urakka-id)
      (throw (SecurityException. (str "Ylläpitokohde " yllapitokohde-id " ei kuulu valittuun urakkaan "
                                      urakka-id " vaan urakkaan " kohteen-urakka))))))

(defn laske-osien-pituudet
  "Hakee tieverkosta osien pituudet tielle. Palauttaa pituuden metreina."
  [db yllapitokohteet]
  (fmap
    (fn [osat]
      (let [tie (:tr-numero (first osat))
            osat (into #{}
                       (comp (mapcat (juxt :tr-alkuosa :tr-loppuosa))
                             (remove nil?))
                       osat)
            min-osa (reduce min 1 osat)
            max-osa (reduce max 1 osat)]
        (into {}
              (map (juxt :osa :pituus))
              (tieverkko/hae-osien-pituudet db tie min-osa max-osa))))
    (group-by :tr-numero yllapitokohteet)))

(defn- yllapitokohde-sisaltaa-kirjauksia?
  "Palauttaa true tai false sen mukaan onko ylläpitokohteeseen liitetty kirjauksia
  (laatupoikkeamia, tarkastuksia, toteumia...)"
  [db yllapitokohde-id]
  (let [kirjaukset (first (q/hae-yllapitokohteeseen-liittyvien-kirjauksien-maara db {:id yllapitokohde-id}))
        kirjauksia-yhteensa (reduce + (vals kirjaukset))]
    (> kirjauksia-yhteensa 0)))

(defn- yllapitokohde-sisaltaa-urakassa-tehtyja-kirjauksia?
  "Palauttaa true tai false sen mukaan onko ylläpitokohteeseen liitetty kirjauksia annetussa
   urakassa (laatupoikkeamia, tarkastuksia, toteumia...)"
  [db yllapitokohde-id urakka-id]
  (let [kirjaukset (first (q/hae-yllapitokohteeseen-urakassa-liittyvien-kirjauksien-maara
                            db {:yllapitokohde_id yllapitokohde-id
                                :urakka_id urakka-id}))
        kirjauksia-yhteensa (reduce + (vals kirjaukset))]
    (> kirjauksia-yhteensa 0)))

(defn- yllapitokohde-sisaltaa-tiemerkintaaikataulun?
  [db yllapitokohde-id]
  (let [aikataulu (first (q/hae-yllapitokohteen-tiemerkintaaikataulu
                           db {:id yllapitokohde-id}))
        aikatauluarvot (vals aikataulu)
        ajalliset-aikatauluarvot (remove nil? aikatauluarvot)]
    (not (empty? ajalliset-aikatauluarvot))))

(defn yllapitokohteen-voi-poistaa?
  [db yllapitokohde-id]
  true ;;FIXME: pikafiksi koska tarkistus hidas
  #_(not (yllapitokohde-sisaltaa-kirjauksia? db yllapitokohde-id))
  )

(defn yllapitokohteen-suorittavan-tiemerkintaurakan-voi-vaihtaa?
  [db yllapitokohde-id tiemerkintaurakka-id]
  true ;;FIXME: pikafiksi koska tarkistus hidas
  #_(if tiemerkintaurakka-id
    (and (not (yllapitokohde-sisaltaa-urakassa-tehtyja-kirjauksia? db yllapitokohde-id tiemerkintaurakka-id))
         (not (yllapitokohde-sisaltaa-tiemerkintaaikataulun? db yllapitokohde-id)))
    true))

(defn hae-urakan-yllapitokohteet [db user {:keys [urakka-id sopimus-id vuosi]}]
  (tarkista-urakkatyypin-mukainen-lukuoikeus db user urakka-id)
  (log/debug "Haetaan urakan ylläpitokohteet.")
  (jdbc/with-db-transaction [db db]
    (let [yllapitokohteet (into []
                                (comp
                                  (map #(assoc % :tila (yllapitokohteet-domain/yllapitokohteen-tarkka-tila %)))
                                  (map #(assoc % :tila-kartalla (yllapitokohteet-domain/yllapitokohteen-tila-kartalla %)))
                                  (map #(konv/string-polusta->keyword % [:paallystysilmoitus-tila]))
                                  (map #(konv/string-polusta->keyword % [:paikkausilmoitus-tila]))
                                  (map #(konv/string-polusta->keyword % [:yllapitokohdetyotyyppi]))
                                  (map #(konv/string-polusta->keyword % [:yllapitokohdetyyppi]))
                                  (map #(q/liita-kohdeosat db % (:id %))))
                                (q/hae-urakan-sopimuksen-yllapitokohteet db {:urakka urakka-id
                                                                             :sopimus sopimus-id
                                                                             :vuosi vuosi}))
          osien-pituudet-tielle (laske-osien-pituudet db yllapitokohteet)
          yllapitokohteet (->> yllapitokohteet
                               (mapv #(assoc % :pituus
                                               (tr/laske-tien-pituus (osien-pituudet-tielle (:tr-numero %)) %)))
                               (mapv #(assoc % :yllapitokohteen-voi-poistaa?
                                               (yllapitokohteen-voi-poistaa? db (:id %)))))]
      (log/debug "[DEBUG] VASTAUS ON " (pr-str yllapitokohteet))
      yllapitokohteet)))

(defn lisaa-yllapitokohteelle-pituus [db {:keys [tr-numero tr-alkuosa tr-loppuosa] :as kohde}]
  (let [osien-pituudet (tr-haku/hae-osien-pituudet db {:tie tr-numero
                                                       :aosa tr-alkuosa
                                                       :losa tr-loppuosa})
        pituus (tr/laske-tien-pituus osien-pituudet kohde)]
    (assoc kohde :pituus pituus)))

(defn paivita-yllapitourakan-geometria [db urakka-id]
  (log/info "Päivitetään urakan " urakka-id " geometriat.")
  (q/paivita-paallystys-tai-paikkausurakan-geometria db {:urakka urakka-id}))