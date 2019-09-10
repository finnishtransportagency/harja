

;; Kiinteähintaisia töitä. Tammi - maaliskuu 2020.
;; Talvihoito 100 + 200 + 300 = 600 e
;; Talvihoito, muut talvihoitotyöt 10 + 20 + 30 = 60 e
;; Talvihoito, muut talvihoitotyöt, Talvihoidon kohotettu laatu 1 + 2 + 3 = 6 e (tässä on testiksi yksi sellainen mäppi, jossa tehtäväryhmä on nil
;; HUOM. Sopimus-tieto pitäisi hakea backendissä, eikä odottaa että se tulee annettuna.
{:kiinteahintaiset-tyot [{:vuosi 2020
                          :kuukausi 1
                          :toimenpideinstanssi 45
                          :tehtavaryhma nil
                          :tehtava nil
                          :summa 100
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 1,
                          :sopimus 2}
                         {:vuosi 2020
                          :kuukausi 2
                          :toimenpideinstanssi 45
                          :tehtavaryhma nil
                          :tehtava nil
                          :summa 200
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 2,
                          :sopimus 2}
                         {:vuosi 2020
                          :kuukausi 3
                          :toimenpideinstanssi 45
                          :tehtavaryhma nil
                          :tehtava nil
                          :summa 300
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 3,
                          :sopimus 2}
                         {:vuosi 2020
                          :kuukausi 1
                          :toimenpideinstanssi 45
                          :tehtavaryhma 5
                          :tehtava nil
                          :summa 10
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 4,
                          :sopimus 2}
                         {:vuosi 2020
                          :kuukausi 2
                          :toimenpideinstanssi 45
                          :tehtavaryhma 5
                          :tehtava nil
                          :summa 20
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 5,
                          :sopimus 2}
                         {:vuosi 2020
                          :kuukausi 3
                          :toimenpideinstanssi 45
                          :tehtavaryhma 5
                          :tehtava nil
                          :summa 30
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 6,
                          :sopimus 2}
                         {:vuosi 2020
                          :kuukausi 1
                          :toimenpideinstanssi 45
                          :tehtavaryhma 5
                          :tehtava 4559
                          :summa 1
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 7,
                          :sopimus 2}
                         {:vuosi 2020
                          :kuukausi 2
                          :toimenpideinstanssi 45
                          :tehtavaryhma nil
                          :tehtava 4559
                          :summa 2
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 8,
                          :sopimus 2}
                         {:vuosi 2020
                          :kuukausi 3
                          :toimenpideinstanssi 45
                          :tehtavaryhma nil
                          :tehtava 4559
                          :summa 3
                          :tpi_nimi "Oulu MHU Talvihoito TP"
                          :id 9,
                          :sopimus 2}],
 ;; Kustannusarvioituja töitä tammi-maaliskuu 2020
 ;; Liikenneympäirstön hoito, nil, nil, laskutettava 3000 + 2000 + 1000
 ;; Liikenneympäirstön hoito, Päällysteiden paikkaus, nil, laskutettava 33 + 22 + 11
 ;; Liikenneympäirstön hoito, nil, nil, äkillinen hoitotyö 0 + 0 + 10000
 ;; Liikenneympäirstön hoito, nil, nil, vahinkojen korjaus 0 + 0 + 10000
 :kustannusarvioidut-tyot [{:vuosi 2020
                            :kuukausi 1
                            :toimenpideinstanssi 46
                            :tehtavaryhma nil
                            :tehtava nil
                            :tyyppi "laskutettava-tyo"
                            :summa 3000
                            :tpi_nimi "Oulu MHU Liikenneympäristön hoito TP"
                            :id 10,
                            :sopimus 2}
                           {:vuosi 2020
                            :kuukausi 2
                            :toimenpideinstanssi 46
                            :tehtavaryhma nil
                            :tehtava nil
                            :tyyppi "laskutettava-tyo"
                            :summa 2000
                            :tpi_nimi "Oulu MHU Liikenneympäristön hoito TP"
                            :id 11,
                            :sopimus 2}
                           {:vuosi 2020
                            :kuukausi 3
                            :toimenpideinstanssi 46
                            :tehtavaryhma nil
                            :tehtava nil
                            :tyyppi "laskutettava-tyo"
                            :summa 1000
                            :tpi_nimi "Oulu MHU Liikenneympäristön hoito TP"
                            :id 12,
                            :sopimus 2}
                           {:vuosi 2020
                            :kuukausi 1
                            :toimenpideinstanssi 46
                            :tehtavaryhma 39
                            :tehtava nil
                            :tyyppi "laskutettava-tyo"
                            :summa 33
                            :tpi_nimi "Oulu MHU Liikenneympäristön hoito TP"
                            :id 13,
                            :sopimus 2}
                           {:vuosi 2020
                            :kuukausi 2
                            :toimenpideinstanssi 46
                            :tehtavaryhma 39
                            :tehtava nil
                            :tyyppi "laskutettava-tyo"
                            :summa 22
                            :tpi_nimi "Oulu MHU Liikenneympäristön hoito TP"
                            :id 14,
                            :sopimus 2}
                           {:vuosi 2020
                            :kuukausi 3
                            :toimenpideinstanssi 46
                            :tehtavaryhma 39
                            :tehtava nil
                            :tyyppi "laskutettava-tyo"
                            :summa 11
                            :tpi_nimi "Oulu MHU Liikenneympäristön hoito TP"
                            :id 15,
                            :sopimus 2}
                           {:vuosi 2020
                            :kuukausi 3
                            :toimenpideinstanssi 46
                            :tehtavaryhma nil
                            :tehtava nil
                            :tyyppi "akillinen-hoitotyo"
                            :summa 10000
                            :tpi_nimi "Oulu MHU Liikenneympäristön hoito TP"
                            :id 16,
                            :sopimus 2}
                           {:vuosi 2020
                            :kuukausi 3
                            :toimenpideinstanssi 46
                            :tehtavaryhma nil
                            :tehtava nil
                            :tyyppi "vahinkojen-korjaukset"
                            :summa 10000
                            :tpi_nimi "Oulu MHU Liikenneympäristön hoito TP"
                            :id 17,
                            :sopimus 2}],
 ;; Yksikköhintaisia töitä
 :yksikkohintaiset-tyot [
                         {:kuukausi 9
                          :vuosi 2020
                          :toimenpideinstanssi 48
                          :yksikko "h",
                          :arvioitu_kustannus nil,
                          :tehtava 4613,
                          :urakka 4,
                          :yksikkohinta 200.0,
                          :maara 19.0,
                          :id 117,
                          :tehtavan_nimi "Hoitourakan työnjohto",
                          :sopimus 2,
                          :tehtavan_id 4613}
                         {:kuukausi 1
                          :vuosi 2020
                          :toimenpideinstanssi 48
                          :yksikko "h",
                          :arvioitu_kustannus nil,
                          :tehtava 4613,
                          :urakka 4,
                          :yksikkohinta 100.0,
                          :maara 3.0,
                          :id 116,
                          :tehtavan_nimi "Hoitourakan työnjohto",
                          :sopimus 2,
                          :tehtavan_id 4613}
                         {:kuukausi 9
                          :vuosi 2020
                          :toimenpideinstanssi 48
                          :yksikko "kpl",
                          :arvioitu_kustannus nil,
                          :tehtava 4615,
                          :urakka 4,
                          :yksikkohinta 2.0,
                          :maara 20.0,
                          :id 136,
                          :tehtavan_nimi "Hoito- ja korjaustöiden pientarvikevarasto",
                          :sopimus 2,
                          :tehtavan_id 4615}
                         {:kuukausi 1
                          :vuosi 2020
                          :toimenpideinstanssi 48
                          :yksikko "ha",
                          :arvioitu_kustannus nil,
                          :tehtava 4615,
                          :urakka 4,
                          :yksikkohinta 100.0,
                          :maara 60.0,
                          :id 118,
                          :tehtavan_nimi "Hoito- ja korjaustöiden pientarvikevarasto",
                          :sopimus 2,
                          :tehtavan_id 4615}
                         {:kuukausi 9
                          :vuosi 2020
                          :toimenpideinstanssi 48
                          :yksikko "jm",
                          :arvioitu_kustannus nil,
                          :tehtava 4615,
                          :urakka 4,
                          :yksikkohinta 100.0,
                          :maara 180.0,
                          :id 119,
                          :tehtavan_nimi "Hoito- ja korjaustöiden pientarvikevarasto",
                          :sopimus 2,
                          :tehtavan_id 4615}
                         {:kuukausi 1
                          :vuosi 2020
                          :toimenpideinstanssi 48
                          :yksikko "vrk",
                          :arvioitu_kustannus nil,
                          :tehtava 4618,
                          :urakka 4,
                          :yksikkohinta 525.5,
                          :maara 3.0,
                          :id 6,
                          :tehtavan_nimi "Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut",
                          :sopimus 2,
                          :tehtavan_id 4618}
                         {:kuukausi 9
                          :vuosi 2020
                          :toimenpideinstanssi 48
                          :yksikko "vrk",
                          :arvioitu_kustannus nil,
                          :tehtava 4618,
                          :urakka 4,
                          :yksikkohinta 525.5,
                          :maara 9.0,
                          :id 7,
                          :tehtavan_nimi "Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut",
                          :sopimus 2,
                          :tehtavan_id 4618}]}


