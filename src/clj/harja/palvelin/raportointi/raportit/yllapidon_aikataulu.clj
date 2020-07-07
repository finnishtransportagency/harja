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
            [specql.core :refer [fetch]]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [taoensso.timbre :as log]))

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
      :sopimus-id (::sopimus/id (sopimus/ainoa-paasopimus sopimukset)))))

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
    :fmt yllapitokohteet-domain/yllapitoluokkanumero->lyhyt-nimi
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

(defn- aikataulun-comparator
  "Järjestää aikataulun mukaan, puuttuvan aikataulut viimeiseksi"
  [x y]
  (let [c (cond
            (and  (nil? (:aikataulu-kohde-alku x))
                  (nil? (:aikataulu-kohde-alku y)))
            0

            (nil? (:aikataulu-kohde-alku x))
            1
            (nil? (:aikataulu-kohde-alku y))
            -1

            :else
            (compare (:aikataulu-kohde-alku x)
                     (:aikataulu-kohde-alku y)))]
    (if (not= c 0)
      c
      (let [c (compare (:kohdenumero x )
                       (:kohdenumero y))]
        c))))

(defn suorita [db user {jarjestys :jarjestys
                        nayta-tarkka-aikajana? :nayta-tarkka-aikajana?
                        nayta-valitavoitteet? :nayta-valitavoitteet?
                        vuosi :vuosi :as parametrit}]
  (let [parametrit (parametrit-urakan-tiedoilla db parametrit)
        aikataulu (yllapitokohteet/hae-urakan-aikataulu db user parametrit)
        aikataulu (sort (case jarjestys
                             :aika aikataulun-comparator
                             :kohdenumero #(compare (:kohdenumero %1) (:kohdenumero %2))
                             :tr #(compare (tr/tieosoitteen-jarjestys %1)
                                           (tr/tieosoitteen-jarjestys %2))

                             aikataulun-comparator)
                           aikataulu)
        aikajanan-rivit (some->> aikataulu
                          (map #(aikataulu/aikataulurivi-jana % {:nayta-tarkka-aikajana? nayta-tarkka-aikajana?}))
                             (filter #(not (empty? (:harja.ui.aikajana/ajat %)))))
        sarakkeet (filter some? (kohdeluettelo-sarakkeet (:tyyppi parametrit)))]
    [:raportti {:nimi (str "Ylläpidon aikataulu" (when vuosi
                                                   (str " vuonna " vuosi)))
                :orientaatio :landscape}
     [:aikajana {}
      ;; Välitavoitteita ei piirretä PDF-raporttiin, koska tod.näk. niitä ei siinä haluta nähdä.
      ;; Järkevä käyttö vaatisi muutenkin hoverointia, mikä ei PDF-raportilla toimi.
      ;; Jos välitavoitteet kuitenkin rapsallekin halutaan, niin tästä voi passata eteenpäin.
      aikajanan-rivit]
     [:taulukko {:otsikko "Kohdeluettelo"}
      (mapv #(dissoc % :fmt :hae) sarakkeet)

      (mapv (apply juxt (map fmt sarakkeet))
            aikataulu)]]))
