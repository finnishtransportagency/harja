(ns harja.palvelin.raportointi.raportit.materiaali
  "Materiaaliraportti"
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.materiaalit :as materiaalit-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.materiaali :as materiaalidomain]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko] :as yleinen]))

(defn muodosta-materiaaliraportti-urakalle [db {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan toteutuneet materiaalit raporttia varten: " urakka-id alkupvm loppupvm)
  (let [toteuma-parametrit [db
                            urakka-id
                            (konv/sql-timestamp alkupvm)
                            (konv/sql-timestamp loppupvm)]
        toteutuneet-materiaalit (into []
                                  (apply materiaalit-q/hae-urakan-toteutuneet-materiaalit-raportille toteuma-parametrit))
        suunnitellut-materiaalit (into []
                                   (apply materiaalit-q/hae-urakan-suunnitellut-materiaalit-raportille toteuma-parametrit))
        suunnitellut-materiaalit-ilman-toteumia (filter
                                                  (fn [materiaali]
                                                    (not-any?
                                                      (fn [toteuma] (= (:materiaali-nimi toteuma) (:materiaalinimi materiaali)))
                                                      toteutuneet-materiaalit))
                                                  suunnitellut-materiaalit)
        lopullinen-tulos (mapv
                           (fn [materiaalitoteuma]
                             (if (nil? (:kokonaismaara materiaalitoteuma))
                               (assoc materiaalitoteuma :kokonaismaara 0)
                               materiaalitoteuma))
                           (reduce conj toteutuneet-materiaalit suunnitellut-materiaalit-ilman-toteumia))]
    (sort-by :elynumero lopullinen-tulos)))

(defn muodosta-materiaaliraportti-hallintayksikolle [db {:keys [elinvoimakeskus-id alkupvm loppupvm]}]
  (log/debug "Haetaan hallintayksikon toteutuneet materiaalit raporttia varten: " elinvoimakeskus-id alkupvm loppupvm)
  (let [toteutuneet-materiaalit (into []
                                  (materiaalit-q/hae-hallintayksikon-toteutuneet-materiaalit-raportille db
                                    {:alku (konv/sql-timestamp alkupvm)
                                     :loppu (konv/sql-timestamp loppupvm)
                                     :elinvoimakeskus elinvoimakeskus-id}))]
    toteutuneet-materiaalit))

(defn muodosta-materiaaliraportti-koko-maalle [db {:keys [alkupvm loppupvm]}]
  (log/debug "Haetaan koko maan toteutuneet materiaalit raporttia varten: " alkupvm loppupvm)
  (let [toteutuneet-materiaalit (into []
                                  (materiaalit-q/hae-koko-maan-toteutuneet-materiaalit-raportille db
                                    {:alku (konv/sql-timestamp alkupvm)
                                     :loppu (konv/sql-timestamp loppupvm)}))]
    toteutuneet-materiaalit))

(defn- materiaalin-otsikko [t]
  (str (:materiaali-nimi t) " (" (:materiaali-yksikko t) ")"))

(defn- materiaalin-otsikko-sarakeen-nimeen [nimi]
  (if-not (= "Talvisuola (t)" nimi)
    nimi
    ;; Osa käyttäjistä on sekoittanut Talvisuola nimen tarkoittavan kaikkea käytettyä
    ;; talvisuolaa. Tehdään siihen ero kertomalla että tämä on rakeista NaCl:ia
    "Talvisuola, NaCl (t)"))

(defn suorita [db user {:keys [urakka-id
                               elinvoimakeskus-id alkupvm loppupvm urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                    elinvoimakeskus-id :elinvoimakeskus
                    :else :koko-maa)
        toteumat
        (cond
          (and urakka-id alkupvm loppupvm)
          (muodosta-materiaaliraportti-urakalle db {:urakka-id urakka-id
                                                    :alkupvm alkupvm
                                                    :loppupvm loppupvm})


          (and elinvoimakeskus-id alkupvm loppupvm)
          (muodosta-materiaaliraportti-hallintayksikolle db
            {:elinvoimakeskus-id elinvoimakeskus-id
             :alkupvm alkupvm
             :loppupvm loppupvm
             :urakkatyyppi urakkatyyppi})

          (and alkupvm loppupvm)
          (muodosta-materiaaliraportti-koko-maalle db {:alkupvm alkupvm
                                                       :loppupvm loppupvm
                                                       :urakkatyyppi urakkatyyppi}))
        raportin-nimi "Materiaaliraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :elinvoimakeskus (:nimi (first (hallintayksikot-q/hae-organisaatio db elinvoimakeskus-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        ;; Aluksi pitää laittaa materiaalit järjestykseen nimen (string) perusteella, sitten liittää
        ;; jokaiseen mukaan yksikkö, pitäen yllä alkuperäinen järjestys.
        materiaaliotsikot (mapv
                            (fn [materiaalin-nimi]
                              (some (fn [t]
                                      (when (= (:materiaali-nimi t) materiaalin-nimi)
                                        (materiaalin-otsikko t)))
                                toteumat))
                            (sort-by materiaalidomain/materiaalien-jarjestys (distinct
                                                                               (map
                                                                                 #(str (:materiaali-nimi %))
                                                                                 toteumat))))
        toteumat-urakan-mukaan (when (not= konteksti :koko-maa)
                                 (group-by :urakka-nimi toteumat))
        toteumat-evkn-mukaan (when (= konteksti :koko-maa)
                               (map
                                 (fn [evk]
                                   (let [evkn-toteumat (filter #(= evk (:elinvoimakeskusnumero %)) toteumat)]
                                     (vector (:elinvoimakeskus-nimi (first evkn-toteumat))
                                       evkn-toteumat)))
                                 (sort (distinct (map :elinvoimakeskusnumero toteumat)))))]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :viimeinen-rivi-yhteenveto? true
                 :sivuttain-rullattava? true
                 :ensimmainen-sarake-sticky? true
                 :sheet-nimi raportin-nimi}
      (into []
        (concat
          [{:otsikko (if (= konteksti :koko-maa) "Hallintayksikkö" "Urakka")}]
          (map (fn [mat]
                 {:otsikko (materiaalin-otsikko-sarakeen-nimeen mat) :fmt :numero})
            (cons "Kaikki talvisuola yhteensä" materiaaliotsikot))))
      (keep identity
        (into
          []
          (concat
            ;; Tehdään rivi jokaiselle alueelle, jossa sen yhteenlasketut toteumat
            (for [[alue toteumat] (or toteumat-urakan-mukaan toteumat-evkn-mukaan)]
              (into []
                (concat [alue]
                  (let [toteumat-materiaalin-mukaan (group-by materiaalin-otsikko toteumat)]
                    (cons
                      (reduce + (keep :kokonaismaara (filter #(= "talvisuola" (:materiaalityyppi %)) toteumat)))
                      (for [m materiaaliotsikot]
                        (reduce + (keep :kokonaismaara (toteumat-materiaalin-mukaan m)))))))))

            ;; Tehdään yhteensä rivi, jossa kaikki toteumat lasketaan yhteen materiaalin perusteella
            (when (seq toteumat)
              [(concat ["Yhteensä"]
                 (let [toteumat-materiaalin-mukaan (group-by materiaalin-otsikko toteumat)]
                   (cons
                     (reduce + (keep :kokonaismaara (filter #(= "talvisuola" (:materiaalityyppi %)) toteumat)))
                     (for [m materiaaliotsikot]
                       (reduce + (keep :kokonaismaara (toteumat-materiaalin-mukaan m)))))))]))))]
     (when-not (empty? toteumat)
       [:teksti (str "Formiaatteja ei lasketa talvisuolan kokonaiskäyttöön. \n")])
     [:teksti (str yleinen/materiaalitoteumien-paivitysinfo)]]))

    
