(ns harja.views.urakka.muut-kustannukset)

(defonce kustannukset-atom (atom {}))

;; muokkaus yllapitokohteet-viewin pohjalla wip
(defn muut-kustannukset [urakka]
  (komp/luo
   (fn [urakka kustannukset-atom]
     [:div.muut-kustannukset
      [grid/grid
       {:otsikko "Muut kustannukset"
        :tyhja (if (nil? @kustannukset-atom) [ajax-loader "Haetaan kustannuksia..."] "Ei kustannuksia")
        ;; :vetolaatikot
        ;; (into {}
        ;;       (map (juxt
        ;;             :id
        ;;             (fn [rivi]
        ;;               [kohteen-vetolaatikko urakka kustannukset-atom rivi])))
        ;;       @kustannukset-atom)
        :tallenna #(log "muut-kustannukset: tallenna kutsuttu")
        :muutos (fn [grid]
                  ;; ??
                  ; (hae-osan-pituudet grid osan-pituudet-teille)
                  ;(validoi-tr-osoite grid tr-sijainnit tr-virheet)
                  )
        :voi-lisata? true
        :esta-poistaminen? (fn [rivi] (or (not (nil? (:paallystysilmoitus-id rivi))) ;; <- tahan tsekkaus onko kohdistamaton sanktio vai suoraan syötetty?
                                          (not (nil? (:paikkausilmoitus-id rivi)))))
        :esta-poistaminen-tooltip
        (fn [_] "Kohteeseen liittymättömästä sanktiosta johtuvaa kustannusta ei voi poistaa.")}
       #_(into []
             (concat
              [{:tyyppi :vetolaatikon-tila :leveys haitari-leveys}
               {:otsikko "Koh\u00ADde\u00ADnu\u00ADme\u00ADro" :nimi :kohdenumero
                :tyyppi :string :leveys id-leveys
                :validoi [[:uniikki "Sama kohdenumero voi esiintyä vain kerran."]]}
               {:otsikko "Koh\u00ADteen ni\u00ADmi" :nimi :nimi
                :tyyppi :string :leveys kohde-leveys}]
              (tierekisteriosoite-sarakkeet
               tr-leveys
               [nil
                nil
                {:nimi :tr-numero :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                {:nimi :tr-ajorata :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                {:nimi :tr-kaista :muokattava? (constantly (not (:yha-sidottu? optiot)))}
                {:nimi :tr-alkuosa :validoi [(partial validoi-kohteen-osoite :tr-alkuosa)]}
                {:nimi :tr-alkuetaisyys :validoi [(partial validoi-kohteen-osoite :tr-alkuetaisyys)]}
                {:nimi :tr-loppuosa :validoi [(partial validoi-kohteen-osoite :tr-loppuosa)]}
                {:nimi :tr-loppuetaisyys :validoi [(partial validoi-kohteen-osoite :tr-loppuetaisyys)]}])
              [{:otsikko "KVL"
                :nimi :keskimaarainen-vuorokausiliikenne :tyyppi :numero :leveys kvl-leveys
                :muokattava? (constantly (not (:yha-sidottu? optiot)))}
               {:otsikko "YP-lk"
                :nimi :yllapitoluokka :tyyppi :numero :leveys yllapitoluokka-leveys
                :muokattava? (constantly (not (:yha-sidottu? optiot)))}

               (when (= (:kohdetyyppi optiot) :paallystys)
                 {:otsikko "Tar\u00ADjous\u00ADhinta" :nimi :sopimuksen-mukaiset-tyot
                  :fmt fmt/euro-opt :tyyppi :numero :leveys tarjoushinta-leveys :tasaa :oikea})
               (when (= (:kohdetyyppi optiot) :paallystys)
                 {:otsikko "Mää\u00ADrä\u00ADmuu\u00ADtok\u00ADset"
                  :nimi :maaramuutokset :muokattava? (constantly false)
                  :fmt fmt/euro-opt :tyyppi :numero :leveys maaramuutokset-leveys :tasaa :oikea})
               (when (= (:kohdetyyppi optiot) :paikkaus)
                 {:otsikko "Toteutunut hinta" :nimi :toteutunut-hinta
                  :muokattava? (constantly false)
                  :fmt fmt/euro-opt :tyyppi :numero :leveys toteutunut-hinta-leveys
                  :tasaa :oikea})
               {:otsikko "Ar\u00ADvon muu\u00ADtok\u00ADset" :nimi :arvonvahennykset :fmt fmt/euro-opt
                :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea}
               {:otsikko "Sak\u00ADko/bo\u00ADnus" :nimi :bonukset-ja-sakot :fmt fmt/euro-opt
                :tyyppi :numero :leveys arvonvahennykset-leveys :tasaa :oikea
                :muokattava? (constantly false)}
               {:otsikko "Bi\u00ADtumi-in\u00ADdek\u00ADsi" :nimi :bitumi-indeksi
                :fmt fmt/euro-opt
                :tyyppi :numero :leveys bitumi-indeksi-leveys :tasaa :oikea}
               {:otsikko "Kaa\u00ADsu\u00ADindeksi" :nimi :kaasuindeksi :fmt fmt/euro-opt
                :tyyppi :numero :leveys kaasuindeksi-leveys :tasaa :oikea}
               {:otsikko (str "Ko\u00ADko\u00ADnais\u00ADhinta"
                              " (ind\u00ADek\u00ADsit mu\u00ADka\u00ADna)")
                :muokattava? (constantly false)
                :nimi :kokonaishinta :fmt fmt/euro-opt :tyyppi :numero :leveys yhteensa-leveys
                :tasaa :oikea
                :hae (fn [rivi] (+ (:sopimuksen-mukaiset-tyot rivi)
                                   (:maaramuutokset rivi)
                                   (:toteutunut-hinta rivi)
                                   (:arvonvahennykset rivi)
                                   (:bonukset-ja-sakot rivi)
                                   (:bitumi-indeksi rivi)
                                   (:kaasuindeksi rivi)))}]))
       ;; (sort-by tr/tiekohteiden-jarjestys @kustannukset-atom) ;; miten sorttaus?
       ]])))
