(ns harja.palvelin.raportointi.vastaanottotarkastus-mhu-test-apurit
  (:require [harja.testi :refer [+kayttaja-jvh+ hae-kajaanin-maanteiden-hoitourakan-2025-2030-id jarjestelma]]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu :as vastaanottotarkastus-mhu]))

(def ^:private testi-hoitokaudet
  [{:alkupvm #inst "2021-10-01T00:00:00.000-00:00"
    :loppupvm #inst "2022-09-30T23:59:59.000-00:00"}
   {:alkupvm #inst "2022-10-01T00:00:00.000-00:00"
    :loppupvm #inst "2023-09-30T23:59:59.000-00:00"}])

(defn muodosta-testiraportti [valikatselmus-tehty?]
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        lupaustiedot (fn [_ {:keys [valittu-hoitokausi]}]
                       (if (= (first valittu-hoitokausi) #inst "2021-10-01T00:00:00.000-00:00")
                         {:lupaus-sitoutuminen {:pisteet 70}
                          :yhteenveto {:valikatselmus-tehty-urakalle? valikatselmus-tehty?
                                       :pisteet {:toteuma 65}}}
                         {:lupaus-sitoutuminen {:pisteet 80}
                          :yhteenveto {:valikatselmus-tehty-urakalle? valikatselmus-tehty?
                                       :pisteet {:toteuma 75}}}))]
    (with-redefs [materiaalit-kyselyt/hae-talvisuolan-kokonaismaara (fn [_ _] [{:kokonaismaara 1000M}])
                  lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle lupaustiedot
                  valikatselmus-q/hae-bonukset (fn [_ {:keys [alkupvm]}]
                                                 (if (= alkupvm (-> testi-hoitokaudet first :alkupvm))
                                                   [{:rahasumma 100M}]
                                                   [{:rahasumma 200M}]))
                  valikatselmus-q/hae-sanktiot (fn [_ {:keys [alkupvm]}]
                                                 (cond
                                                   (= alkupvm (-> testi-hoitokaudet first :alkupvm))
                                                   [{:maara -25M}]
                                                   (= alkupvm #inst "2021-01-01T00:00:00.000-00:00")
                                                   [{:sakkoryhma :talvisuolan_ylitys :maara 100M}]
                                                   :else
                                                   [{:maara -50M}]))
                  rahavaraus-kyselyt/hae-urakan-rahavaraukset
                  (fn [_ _]
                    [{:id 1 :nimi "Äkilliset hoitotyöt"}
                     {:id 2 :nimi "Vahinkojen korjaukset"}
                     {:id 3 :nimi "Tilaajan rahavaraus kannustinjärjestelmään"}
                     {:id 4 :nimi "Muu rahavaraus 1"}
                     {:id 5 :nimi "Muu rahavaraus 2"}])
                  rahavaraus-kyselyt/muutosten-rahavaraukset
                  (fn [_ _ hoitokauden-alkuvuosi]
                    (if (= hoitokauden-alkuvuosi 2021)
                      [{:id 1 :summa-indeksikorjattu 100M :toteumat 80M :tavoitehinnan-muutos -20M}
                       {:id 2 :summa-indeksikorjattu 50M :toteumat 40M :tavoitehinnan-muutos -10M}
                       {:id 3 :summa-indeksikorjattu 25M :toteumat 20M :tavoitehinnan-muutos -5M}
                       {:id 4 :summa-indeksikorjattu 10M :toteumat 8M :tavoitehinnan-muutos -2M}
                       {:id 5 :summa-indeksikorjattu 20M :toteumat 15M :tavoitehinnan-muutos -5M}
                       {:id :yhteenveto :summa-indeksikorjattu 205M :toteumat 163M :tavoitehinnan-muutos -42M}]
                      [{:id 1 :summa-indeksikorjattu 200M :toteumat 150M :tavoitehinnan-muutos -50M}
                       {:id 2 :summa-indeksikorjattu 100M :toteumat 90M :tavoitehinnan-muutos -10M}
                       {:id 3 :summa-indeksikorjattu 50M :toteumat 45M :tavoitehinnan-muutos -5M}
                       {:id 4 :summa-indeksikorjattu 30M :toteumat 20M :tavoitehinnan-muutos -10M}
                       {:id 5 :summa-indeksikorjattu 40M :toteumat 30M :tavoitehinnan-muutos -10M}
                       {:id :yhteenveto :summa-indeksikorjattu 420M :toteumat 335M :tavoitehinnan-muutos -85M}]))]
      (vastaanottotarkastus-mhu/suorita (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id}))))
