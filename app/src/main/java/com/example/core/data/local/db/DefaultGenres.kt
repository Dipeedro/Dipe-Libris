package com.example.core.data.local.db

data class DefaultGenreSeed(
  val slug: String,
  val namePt: String,
  val nameEn: String,
)

object DefaultGenres {
  val seeds: List<DefaultGenreSeed> =
    listOf(
      DefaultGenreSeed("literary-fiction", "Ficção literária", "Literary Fiction"),
      DefaultGenreSeed("classics", "Clássicos", "Classics"),
      DefaultGenreSeed("fantasy-scifi", "Fantasia e ficção científica", "Fantasy & Sci-Fi"),
      DefaultGenreSeed("mystery-thriller", "Mistério e suspense", "Mystery & Thriller"),
      DefaultGenreSeed("romance", "Romance", "Romance"),
      DefaultGenreSeed("poetry", "Poesia", "Poetry"),
      DefaultGenreSeed("drama", "Teatro e dramaturgia", "Drama & Plays"),
      DefaultGenreSeed("essays-criticism", "Ensaios e crítica", "Essays & Criticism"),
      DefaultGenreSeed("history", "História", "History"),
      DefaultGenreSeed("philosophy", "Filosofia", "Philosophy"),
      DefaultGenreSeed("biography-memoir", "Biografia e memórias", "Biography & Memoir"),
      DefaultGenreSeed("science-technology", "Ciência e tecnologia", "Science & Technology"),
      DefaultGenreSeed("psychology-society", "Psicologia e sociedade", "Psychology & Society"),
      DefaultGenreSeed("arts-architecture", "Artes e arquitetura", "Arts & Architecture"),
      DefaultGenreSeed("religion-mythology", "Religião e mitologia", "Religion & Mythology"),
      DefaultGenreSeed("academic-reference", "Acadêmico e referência", "Academic & Reference"),
      DefaultGenreSeed("graphic-novels", "Quadrinhos e HQs", "Graphic Novels"),
    )
}
