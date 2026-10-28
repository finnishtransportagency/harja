(ns harja.palvelin.raportointi.vastaanottotarkastus-mhu-test-apurit
  (:require [harja.testi :refer [+kayttaja-jvh+ hae-kajaanin-maanteiden-hoitourakan-2025-2030-id jarjestelma]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.materiaalit :as materiaalit-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.valikatselmus :as valikatselmus-q]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu :as vastaanottotarkastus-mhu]))

(defn- testi-hoitokaudet [urakka-id]
  (->> (urakat-q/hae-urakan-hoitokaudet (:db jarjestelma) urakka-id)
    (sort-by :alkupvm)
    vec))

(defn muodosta-testiraportti [valikatselmus-tehty?]
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        hoitokaudet (testi-hoitokaudet urakka-id)
        ensimmaisen-hoitokauden-alkupvm (-> hoitokaudet first :alkupvm)
        toisen-hoitokauden-alkupvm (-> hoitokaudet second :alkupvm)
        ensimmaisen-hoitokauden-alkuvuosi (pvm/vuosi ensimmaisen-hoitokauden-alkupvm)
        lupaustiedot (fn [_ {:keys [valittu-hoitokausi]}]
                       (if (= (first valittu-hoitokausi) ensimmaisen-hoitokauden-alkupvm)
                         {:lupaus-sitoutuminen {:pisteet 70}
                          :yhteenveto {:valikatselmus-tehty-urakalle? valikatselmus-tehty?
                                       :pisteet {:toteuma 65}}}
                         {:lupaus-sitoutuminen {:pisteet 80}
                          :yhteenveto {:valikatselmus-tehty-urakalle? valikatselmus-tehty?
                                       :pisteet {:toteuma 75}}}))]
    (with-redefs [materiaalit-kyselyt/hae-talvisuolan-kokonaismaara (fn [_ _] [{:kokonaismaara 1000M}])
                  lupaus-palvelu/hae-urakan-lupaustiedot-hoitokaudelle lupaustiedot
                  valikatselmus-q/hae-bonukset (fn [_ {:keys [alkupvm]}]
                                                 (if (= alkupvm ensimmaisen-hoitokauden-alkupvm)
                                                   [{:rahasumma 100M}]
                                                   [{:rahasumma 200M}]))
                  valikatselmus-q/hae-sanktiot (fn [_ {:keys [alkupvm]}]
                                                 (cond
                                                   (= alkupvm ensimmaisen-hoitokauden-alkupvm)
                                                   [{:maara -25M}]
                                                   (= alkupvm toisen-hoitokauden-alkupvm)
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
                    (if (= hoitokauden-alkuvuosi ensimmaisen-hoitokauden-alkuvuosi)
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
