(ns harja.palvelin.raportointi.raportit.yllapidon-aikataulu
  (:require [harja.ui.aikajana :as aj]
            [harja.pvm :as pvm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]
            [harja.domain.aikataulu :as aikataulu]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.urakka :as urakka]
            [harja.domain.sopimus :as sopimus]
            [specql.core :refer [fetch]]))

(defn- parametrit-urakan-tiedoilla
  "Hae urakan tyyppi ja pääsopimuksen id"
  [db {urakka-id :urakka-id :as parametrit}]
  (let [{tyyppi ::urakka/tyyppi
         sopimukset ::urakka/sopimukset}
        (first (fetch db ::urakka/urakka
                      #{::urakka/tyyppi
                        [::urakka/sopimukset #{::sopimus/id ::sopimus/paasopimus-id}]}
                      {::urakka/id urakka-id}))]
    (assoc parametrit
           :tyyppi (keyword tyyppi)
           :sopimus-id (::sopimus/id (sopimus/paasopimus sopimukset)))))

(defn kohdeluettelo-sarakkeet [urakkatyyppi]
  [{:otsikko "Koh\u00ADde" :leveys 4 :nimi :kohdenumero :tyyppi :string}
   {:otsikko "Nimi" :leveys 8 :nimi :nimi :tyyppi :string}
   {:otsikko "Tieosoite" :nimi :tr-osoite :hae identity
    :fmt #(tr/tierekisteriosoite-tekstina % {:teksti-tie? false})
    :leveys 8 :tasaa :oikea}
   {:otsikko "Ajo\u00ADrata"
    :nimi :tr-ajorata
    :tyyppi :string :tasaa :oikea
    :leveys 2}
   {:otsikko "Kais\u00ADta"
    :nimi :tr-kaista
    :tyyppi :string
    :tasaa :oikea
    :leveys 2}

   {:otsikko "YP-lk"
    :nimi :yllapitoluokka :leveys 2 :tyyppi :string
    ;;:fmt yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi
    }
   (when (= urakkatyyppi :paallystys)
     {:otsikko "Koh\u00ADteen aloi\u00ADtus" :leveys 6 :nimi :aikataulu-kohde-alku
      :tyyppi :pvm :fmt pvm/pvm-opt})
   {:otsikko "Pääl\u00ADlystyk\u00ADsen aloi\u00ADtus" :leveys 6 :nimi :aikataulu-paallystys-alku
    :tyyppi :pvm :fmt pvm/pvm-opt}
   {:otsikko "Pääl\u00ADlystyk\u00ADsen lope\u00ADtus" :leveys 6 :nimi :aikataulu-paallystys-loppu
    :tyyppi :pvm :fmt pvm/pvm-opt}
   {:otsikko "Val\u00ADmis tie\u00ADmerkin\u00ADtään" :leveys 6
    :fmt pvm/pvm-opt
    :nimi :valmis-tiemerkintaan}
   {:otsikko "Tie\u00ADmerkin\u00ADtä val\u00ADmis vii\u00ADmeis\u00ADtään"
    :leveys 6 :nimi :aikataulu-tiemerkinta-takaraja :tyyppi :pvm
    :fmt pvm/pvm-opt}
   {:otsikko "Tiemer\u00ADkinnän aloi\u00ADtus"
    :leveys 6 :nimi :aikataulu-tiemerkinta-alku :tyyppi :pvm
    :fmt pvm/pvm-opt}
   {:otsikko "Tiemer\u00ADkinnän lope\u00ADtus"
    :leveys 6 :nimi :aikataulu-tiemerkinta-loppu :tyyppi :pvm
    :fmt pvm/pvm-opt}
   {:otsikko "Pääl\u00ADlystys\u00ADkoh\u00ADde val\u00ADmis" :leveys 6 :nimi :aikataulu-kohde-valmis :tyyppi :pvm
    :fmt pvm/pvm-opt}])

(defn- fmt [{:keys [nimi fmt hae]}]
  #((or fmt str) (if hae
                   (hae %)
                   (get % nimi))))

(defn suorita [db user {jarjestys :jarjestys :as parametrit}]
  (let [parametrit (parametrit-urakan-tiedoilla db parametrit)
        aikataulu (yllapitokohteet/hae-urakan-aikataulu db user parametrit)
        aikataulu (if (or (nil? jarjestys) (= :aika jarjestys))
                    aikataulu
                    (sort-by (case jarjestys
                               :kohdenumero :kohdenumero
                               :tr tr/tieosoitteen-jarjestys) aikataulu))
        sarakkeet (kohdeluettelo-sarakkeet (:tyyppi parametrit))]
    [:raportti {:nimi "Ylläpidon aikataulu"
                :orientaatio :landscape}
     [:aikajana {}
      (map aikataulu/aikataulurivi-jana aikataulu)]
     [:taulukko {:otsikko "Kohdeluettelo"}
      (mapv #(dissoc % :fmt :hae) sarakkeet)

      (mapv (apply juxt (map fmt sarakkeet))
            aikataulu)]]))
