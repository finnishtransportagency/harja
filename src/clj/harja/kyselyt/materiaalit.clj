(ns harja.kyselyt.materiaalit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/materiaalit.sql"
  {:positional? true})

(declare hae-materiaalikoodit hae-materiaalikoodin-id-nimella listaa-materiaalikoodit hae-materiaaliluokat
  hae-urakan-suunniteltu-materiaalin-kaytto-analytiikalle hae-talvisuolan-materiaaliluokka
  hae-urakan-toteutuneet-materiaalit-raportille hae-urakan-suunnitellut-materiaalit-raportille
  hae-hallintayksikon-toteutuneet-materiaalit-raportille hae-koko-maan-toteutuneet-materiaalit-raportille
  hae-toteumien-tarkat-tiedot-materiaalille poista-toteuma-materiaali!
  hae-suolatoteumien-summatiedot hae-suolatoteumat-tr-valille hae-suolamateriaalit
  paivita-sopimuksen-materiaalin-kaytto paivita-urakan-materiaalin-kaytto-hoitoluokittain
  hae-materiaalikoodit-ilman-talvisuolaa hae-urakan-materiaalit hae-urakassa-kaytetyt-materiaalit
  hae-urakan-toteumat-materiaalille hae-toteuman-materiaalitiedot poista-urakan-materiaalinkaytto!
  poista-materiaalinkaytto-id! paivita-materiaalinkaytto-maara! luo-materiaalinkaytto<!
  paivita-toteuma-materiaali! luo-toteuma-materiaali<! paivita-sopimuksen-materiaalin-kaytto-toteumapvm
  hae-suolauksen-toimenpidekoodi hae-talvisuolan-hoitovuoden-kokonaismaara
  paivita-urakan-materiaalikaytto-hoitoluokittain-muutospaivalla hae-toteuma-id-materiaali-idlla)
