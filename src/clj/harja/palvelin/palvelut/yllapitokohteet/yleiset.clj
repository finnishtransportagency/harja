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
            [harja.domain.oikeudet :as oikeudet])
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

(defn vaadi-yllapitokohde-kuuluu-urakkaan [db urakka-id yllapitokohde]
  "Tarkistaa, että ylläpitokohde kuuluu annettuun urakkaan tai annettu urakka on merkitty
   suorittavaksi tiemerkintäurakakaksi. Jos kumpikaan ei ole totta, heittää poikkeuksen."
  (let [kohteen-urakka (:id (first (q/hae-yllapitokohteen-urakka-id db {:id yllapitokohde})))
        kohteen-suorittava-tiemerkintaurakka (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                           db
                                                           {:id yllapitokohde})))]
    (when (and (not= kohteen-urakka urakka-id)
               (not= kohteen-suorittava-tiemerkintaurakka urakka-id))
      (throw (SecurityException. (str "Ylläpitokohde " yllapitokohde " ei kuulu valittuun urakkaan "
                                      urakka-id " vaan urakkaan " kohteen-urakka
                                      ", eikä valittu urakka myöskään ole kohteen suorittava tiemerkintäurakka"))))))