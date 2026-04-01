package com.example.recipegenie.data

object SampleRecipeData {

    val recipes = listOf(
        Recipe(
            id = "sample_masala_oats",
            title = "Masala Oats Bowl",
            imageUrl = "https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=1200&q=80",
            cookTimeMinutes = 15,
            servings = 2,
            difficulty = "Easy",
            rating = 4.6f,
            category = "breakfast",
            cuisine = "Indian",
            description = "Savory oats with vegetables and warm spices.",
            ingredients = listOf(
                Ingredient("Rolled oats", "1 cup"),
                Ingredient("Onion", "1 small"),
                Ingredient("Tomato", "1 medium"),
                Ingredient("Green peas", "1/4 cup"),
                Ingredient("Turmeric", "1/4 tsp"),
                Ingredient("Water", "2 cups")
            ),
            steps = listOf(
                Step(1, "Saute onion and tomato with a little oil until soft."),
                Step(2, "Add peas, turmeric, salt, and oats. Stir for 1 minute."),
                Step(3, "Pour in water and cook until the oats are creamy."),
                Step(4, "Serve hot with coriander and lemon.")
            ),
            nutrition = Nutrition(280, 9, 44, 7, 6)
        ),
        Recipe(
            id = "sample_avocado_toast",
            title = "Avocado Chili Toast",
            imageUrl = "https://images.unsplash.com/photo-1541519227354-08fa5d50c44d?auto=format&fit=crop&w=1200&q=80",
            cookTimeMinutes = 10,
            servings = 2,
            difficulty = "Easy",
            rating = 4.7f,
            category = "breakfast",
            cuisine = "Global",
            description = "Crisp toast with smashed avocado, chili flakes, and lime.",
            ingredients = listOf(
                Ingredient("Bread slices", "2"),
                Ingredient("Avocado", "1 ripe"),
                Ingredient("Lime juice", "1 tsp"),
                Ingredient("Chili flakes", "1/4 tsp"),
                Ingredient("Salt", "to taste")
            ),
            steps = listOf(
                Step(1, "Toast the bread until golden."),
                Step(2, "Mash avocado with lime juice, chili flakes, and salt."),
                Step(3, "Spread on toast and serve immediately.")
            ),
            nutrition = Nutrition(310, 7, 30, 18, 8)
        ),
        Recipe(
            id = "sample_paneer_wrap",
            title = "Paneer Tikka Wrap",
            imageUrl = "https://images.unsplash.com/photo-1511689660979-10d2b1aada49?auto=format&fit=crop&w=1200&q=80",
            cookTimeMinutes = 20,
            servings = 2,
            difficulty = "Medium",
            rating = 4.8f,
            category = "lunch",
            cuisine = "Indian",
            description = "Soft wraps filled with spiced paneer and veggies.",
            ingredients = listOf(
                Ingredient("Paneer", "200 g"),
                Ingredient("Curd", "2 tbsp"),
                Ingredient("Tikka masala", "1 tbsp"),
                Ingredient("Capsicum", "1/2 cup"),
                Ingredient("Tortilla", "2"),
                Ingredient("Mint chutney", "2 tbsp")
            ),
            steps = listOf(
                Step(1, "Coat paneer with curd and tikka masala."),
                Step(2, "Pan-sear paneer and capsicum until lightly charred."),
                Step(3, "Spread chutney on tortillas and add filling."),
                Step(4, "Roll tightly and serve warm.")
            ),
            nutrition = Nutrition(420, 19, 29, 24, 3)
        ),
        Recipe(
            id = "sample_garlic_pasta",
            title = "Garlic Butter Pasta",
            imageUrl = "https://images.unsplash.com/photo-1621996346565-e3dbc646d9a9?auto=format&fit=crop&w=1200&q=80",
            cookTimeMinutes = 18,
            servings = 2,
            difficulty = "Easy",
            rating = 4.5f,
            category = "dinner",
            cuisine = "Italian",
            description = "Quick pasta tossed with garlic butter and herbs.",
            ingredients = listOf(
                Ingredient("Pasta", "200 g"),
                Ingredient("Garlic", "4 cloves"),
                Ingredient("Butter", "2 tbsp"),
                Ingredient("Parsley", "2 tbsp"),
                Ingredient("Parmesan", "2 tbsp")
            ),
            steps = listOf(
                Step(1, "Boil pasta until al dente."),
                Step(2, "Melt butter and saute garlic until fragrant."),
                Step(3, "Add pasta, parsley, and a splash of pasta water."),
                Step(4, "Top with parmesan and serve.")
            ),
            nutrition = Nutrition(510, 14, 61, 22, 3)
        ),
        Recipe(
            id = "sample_veg_fried_rice",
            title = "Veg Fried Rice",
            imageUrl = "https://images.unsplash.com/photo-1603133872878-684f208fb84b?auto=format&fit=crop&w=1200&q=80",
            cookTimeMinutes = 20,
            servings = 3,
            difficulty = "Easy",
            rating = 4.4f,
            category = "lunch",
            cuisine = "Chinese",
            description = "Fast wok-tossed rice with vegetables and soy.",
            ingredients = listOf(
                Ingredient("Cooked rice", "3 cups"),
                Ingredient("Carrot", "1/2 cup"),
                Ingredient("Beans", "1/2 cup"),
                Ingredient("Soy sauce", "1 tbsp"),
                Ingredient("Spring onion", "2 tbsp")
            ),
            steps = listOf(
                Step(1, "Saute vegetables on high heat."),
                Step(2, "Add rice and soy sauce."),
                Step(3, "Toss quickly until heated through."),
                Step(4, "Finish with spring onion.")
            ),
            nutrition = Nutrition(360, 8, 61, 8, 4)
        ),
        Recipe(
            id = "sample_brownie_mug",
            title = "Chocolate Mug Brownie",
            imageUrl = "https://images.unsplash.com/photo-1606313564200-e75d5e30476c?auto=format&fit=crop&w=1200&q=80",
            cookTimeMinutes = 8,
            servings = 1,
            difficulty = "Easy",
            rating = 4.9f,
            category = "desserts",
            cuisine = "Global",
            description = "Single-serve brownie made in minutes.",
            ingredients = listOf(
                Ingredient("Flour", "4 tbsp"),
                Ingredient("Cocoa powder", "2 tbsp"),
                Ingredient("Sugar", "2 tbsp"),
                Ingredient("Milk", "3 tbsp"),
                Ingredient("Oil", "2 tbsp")
            ),
            steps = listOf(
                Step(1, "Mix all ingredients in a microwave-safe mug."),
                Step(2, "Microwave for 60 to 90 seconds."),
                Step(3, "Rest for 1 minute and enjoy warm.")
            ),
            nutrition = Nutrition(390, 5, 49, 19, 2)
        )
    )
}
